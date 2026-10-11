#!/usr/bin/env python3
"""
KAIRO AI Tools Engine
Database CRUD, Fuzzy Semantic Resolution, FCM Hardware Alarm Dispatching, and Data Exporting.
"""

import os
import re
import csv
import json
import time
import uuid
import logging
from datetime import datetime, timedelta
from difflib import SequenceMatcher
from pathlib import Path
import pytz
import requests

import firebase_admin
from firebase_admin import credentials, messaging

logger = logging.getLogger("KairoTools")

SUPABASE_URL = "https://lswzgwzbvuiolkzvyrof.supabase.co"
SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imxzd3pnd3pidnVpb2xrenZ5cm9mIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTA3ODIxMzIsImV4cCI6MjEwNjM1ODEzMn0.u7iQyOKyjKPblLtDYgoiLAHv4p01lEU2LD5HQQ9-7SA"

BASE_DIR = Path(__file__).resolve().parent
EXPORTS_DIR = BASE_DIR / "exports"
EXPORTS_DIR.mkdir(parents=True, exist_ok=True)
ATTACHMENTS_DIR = BASE_DIR / "attachments"
ATTACHMENTS_DIR.mkdir(parents=True, exist_ok=True)

DEFAULT_TIMEZONE = pytz.timezone("Asia/Kolkata")

# Outbox for sending documents/files back to Telegram
outbox = []

DEFAULT_FCM_TOKEN = os.getenv("DEFAULT_FCM_TOKEN", "")
current_user = {
    "user_code": os.getenv("DEFAULT_USER_CODE", ""),
    "fcm_token": DEFAULT_FCM_TOKEN or None
}

def set_context(user_code: str, fcm_token: str = None):
    current_user["user_code"] = user_code
    current_user["fcm_token"] = fcm_token or DEFAULT_FCM_TOKEN or None

def supabase_headers() -> dict:
    return {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json",
        "Prefer": "return=representation"
    }

# ==========================================
# FCM ALARM DISPATCHER
# ==========================================
def dispatch_alarm_wakeup(task: dict = None) -> bool:
    token = current_user.get("fcm_token")
    if not token:
        logger.warning("No FCM token set in context. Skipping push wakeup.")
        return False

    try:
        data_payload = {
            "action": "SYNC_AND_SCHEDULE",
        }
        if task:
            is_comp = bool(task.get("is_completed", False))
            data_payload.update({
                "id": str(task.get("id", "")),
                "title": str(task.get("title", "")),
                "dueDate": str(task.get("due_date", "Today")),
                "dueTime": str(task.get("due_time", "") or ""),
                "priority": str(task.get("priority", "LOW")),
                "section": str(task.get("section", "TODAY")),
                "is_completed": "true" if is_comp else "false",
                "isCompleted": "true" if is_comp else "false",
            })
            if task.get("completed_at"):
                data_payload["completedAt"] = str(task["completed_at"])
                data_payload["completed_at"] = str(task["completed_at"])
            else:
                data_payload["completedAt"] = ""
                data_payload["completed_at"] = ""
            if task.get("due_date_millis"):
                data_payload["dueDateMillis"] = str(task["due_date_millis"])
            if task.get("notes"):
                data_payload["notes"] = str(task["notes"])
            if task.get("alarm_tone_uri"):
                data_payload["alarmToneUri"] = str(task["alarm_tone_uri"])
            if task.get("alarm_tone_title"):
                data_payload["alarmToneTitle"] = str(task["alarm_tone_title"])

        message = messaging.Message(
            token=token,
            data=data_payload,
            android=messaging.AndroidConfig(priority="high")
        )
        response = messaging.send(message)
        logger.info(f"FCM hardware alarm wake sent: {response}")
        return True
    except Exception as e:
        logger.error(f"FCM dispatch error: {e}")
        return False

# ==========================================
# SUPABASE DATA ACCESS
# ==========================================
def fetch_all_tasks_for_user() -> list:
    user_code = current_user["user_code"]
    try:
        url = f"{SUPABASE_URL}/rest/v1/tasks?user_code=eq.{user_code}&select=*&order=position.asc,created_at.desc"
        res = requests.get(url, headers=supabase_headers(), timeout=10)
        if res.status_code == 200:
            return res.json()
    except Exception as e:
        logger.error(f"Error fetching tasks from Supabase: {e}")
    return []

# ==========================================
# FUZZY & SEMANTIC RESOLVER
# ==========================================
def score_match(query: str, target: str) -> float:
    q = query.lower().strip()
    t = target.lower().strip()
    if not q or not t:
        return 0.0
    if q == t:
        return 1.0
    if q in t or t in q:
        return 0.90

    qw = re.findall(r'\w+', q)
    tw = re.findall(r'\w+', t)
    if not qw or not tw:
        return SequenceMatcher(None, q, t).ratio()

    stem_hits = 0
    for w1 in qw:
        for w2 in tw:
            if w1 in w2 or w2 in w1 or SequenceMatcher(None, w1, w2).ratio() >= 0.72:
                stem_hits += 1
                break
    if qw and stem_hits > 0:
        return 0.70 + 0.25 * (stem_hits / len(qw))

    return SequenceMatcher(None, q, t).ratio()

def resolve_task_by_query(query: str, tasks: list = None) -> tuple[dict | None, list]:
    """
    Finds the best matching task for a user query.
    Returns (best_match_task, candidate_matches).
    """
    if tasks is None:
        tasks = fetch_all_tasks_for_user()
    if not tasks:
        return None, []

    # Exact ID match
    clean_query = query.strip()
    for t in tasks:
        if t.get("id") == clean_query:
            return t, [t]

    scored = []
    for t in tasks:
        title = t.get("title", "")
        notes = t.get("notes") or ""
        s_title = score_match(clean_query, title)
        s_notes = score_match(clean_query, notes) * 0.8
        best_score = max(s_title, s_notes)
        scored.append((best_score, t))

    scored.sort(key=lambda x: x[0], reverse=True)
    high_matches = [t for s, t in scored if s >= 0.55]

    if scored and scored[0][0] >= 0.55:
        return scored[0][1], high_matches
    return None, [t for s, t in scored[:3]]

# ==========================================
# HIERARCHY HELPERS & RECHECK LOGIC (MATCHING ANDROID)
# ==========================================
def get_descendant_ids(task_id: str, all_tasks: list) -> set[str]:
    """Retrieves all recursive child task IDs for a given task ID."""
    result = set()
    queue = [task_id]
    while queue:
        curr = queue.pop(0)
        children = [t for t in all_tasks if t.get("parent_id") == curr]
        for c in children:
            if c["id"] not in result:
                result.add(c["id"])
                queue.append(c["id"])
    return result

def get_ancestor_ids(task_id: str, all_tasks: list) -> list[str]:
    """Retrieves all ancestor task IDs for a given task ID ascending to root."""
    by_id = {t["id"]: t for t in all_tasks}
    ancestors = []
    curr = by_id.get(task_id, {}).get("parent_id")
    visited = set()
    while curr and curr not in visited:
        visited.add(curr)
        ancestors.append(curr)
        curr = by_id.get(curr, {}).get("parent_id")
    return ancestors

def recheck_parent_completion(parent_id: str, tasks: list, now_ms: int) -> list[dict]:
    """
    Rechecks completion upward if all remaining subtasks under a parent are done.
    Matches TaskRepository.kt:610-643 exactly.
    """
    if not parent_id:
        return []
    curr = parent_id
    visited = set()
    changed = []
    by_id = {t["id"]: t for t in tasks}
    while curr and curr not in visited:
        visited.add(curr)
        parent = by_id.get(curr)
        if not parent:
            break
        children = [t for t in tasks if t.get("parent_id") == curr]
        if children and all(t.get("is_completed") for t in children):
            if not parent.get("is_completed"):
                parent["is_completed"] = True
                parent["section"] = "COMPLETED"
                parent["completed_at"] = now_ms
                parent["updated_at"] = now_ms
                changed.append(parent)
            curr = parent.get("parent_id")
        elif any(not t.get("is_completed") for t in children):
            if parent.get("is_completed"):
                parent["is_completed"] = False
                due_ms = parent.get("due_date_millis") or now_ms
                parent["section"] = determine_section(due_ms, False, parent.get("due_date", "Today"))
                parent["completed_at"] = None
                parent["updated_at"] = now_ms
                changed.append(parent)
            curr = parent.get("parent_id")
        else:
            break
    return changed

# ==========================================
# DATE & TIME COMPUTATION HELPER
# ==========================================
def compute_due_millis(due_date_str: str, due_time_str: str = None) -> int:
    """
    Computes trigger epoch millis matching Android TaskViewModel.computeDueDateMillis.
    Defaults to 12:00 PM (noon) if due_time is not provided.
    """
    now = datetime.now(DEFAULT_TIMEZONE)
    target_date = now.date()

    if due_date_str:
        clean_date = due_date_str.strip().lower()
        if "tomorrow" in clean_date:
            target_date = now.date() + timedelta(days=1)
        elif "yesterday" in clean_date:
            target_date = now.date() - timedelta(days=1)
        elif "today" in clean_date:
            target_date = now.date()
        else:
            for fmt in ["%b %d", "%B %d", "%Y-%m-%d", "%d/%m/%Y", "%d %b %Y", "%b %d, %Y", "%EEE, %b %d"]:
                try:
                    parsed = datetime.strptime(due_date_str.strip(), fmt)
                    y = parsed.year if parsed.year > 1970 else target_date.year
                    target_date = target_date.replace(year=y, month=parsed.month, day=parsed.day)
                    break
                except Exception:
                    pass

    hour = 12
    minute = 0
    if due_time_str and due_time_str.strip():
        for fmt in ["%I:%M %p", "%I %p", "%H:%M", "%H"]:
            try:
                t = datetime.strptime(due_time_str.strip().upper(), fmt)
                hour = t.hour
                minute = t.minute
                break
            except Exception:
                pass

    target_dt = DEFAULT_TIMEZONE.localize(datetime(
        year=target_date.year,
        month=target_date.month,
        day=target_date.day,
        hour=hour,
        minute=minute,
        second=0
    ))
    return int(target_dt.timestamp() * 1000)

def determine_section(due_millis: int, is_completed: bool, due_date_str: str = "Today") -> str:
    """
    Determines section matching Android TaskRepository.kt:702-708 exactly:
    - If completed: COMPLETED
    - If dueDate contains 'Yesterday': OVERDUE
    - If dueDate contains 'Today': TODAY (never overdue during today!)
    - If due_millis < todayStart: OVERDUE
    - If todayStart <= due_millis < (todayStart + 86400000): TODAY
    - Else: UPCOMING
    """
    if is_completed:
        return "COMPLETED"

    clean_date = (due_date_str or "Today").strip().lower()
    if "yesterday" in clean_date:
        return "OVERDUE"
    if "today" in clean_date:
        return "TODAY"

    now_dt = datetime.now(DEFAULT_TIMEZONE)
    today_start_dt = now_dt.replace(hour=0, minute=0, second=0, microsecond=0)
    today_start_ms = int(today_start_dt.timestamp() * 1000)
    today_end_ms = today_start_ms + 86400 * 1000

    if due_millis is not None and due_millis > 0:
        if due_millis < today_start_ms:
            return "OVERDUE"
        elif today_start_ms <= due_millis < today_end_ms:
            return "TODAY"
        else:
            return "UPCOMING"

    return "TODAY"

# ==========================================
# NATIVE GEMINI TOOL FUNCTIONS
# ==========================================

def get_recent_tones() -> str:
    """Returns a list of unique alarm tone names recently used in Kairo tasks."""
    tasks = fetch_all_tasks_for_user()
    tones = []
    for t in tasks:
        title = t.get("alarm_tone_title")
        if title and title not in tones and title != "NONE":
            tones.append(title)
    if not tones:
        return "No custom tones set yet. 'Default Tone' is active."
    return "Recently used tones: " + ", ".join(tones)

def add_task(
    title: str,
    due_date: str = "Today",
    due_time: str = None,
    priority: str = "LOW",
    notes: str = None,
    repeat_type: str = "NONE",
    repeat_days: str = None,
    repeat_dates: str = None,
    tone: str = "Default Tone",
    location: str = None,
    photos: str = None,
    parent_task_query: str = None
) -> str:
    """
    Creates a new task in Kairo. Matches Android TaskRepository.addTask.
    If added under a completed parent, reopens parent and all ancestors.
    """
    if not title or not title.strip():
        return "ERROR: Task name/title is mandatory."

    user_code = current_user["user_code"]
    task_id = str(uuid.uuid4())
    eff_due_date = due_date.strip() if due_date else "Today"
    eff_due_time = due_time.strip() if due_time else None
    due_millis = compute_due_millis(eff_due_date, eff_due_time)
    now_ms = int(time.time() * 1000)

    all_tasks = fetch_all_tasks_for_user()

    # Resolve parent if specified
    parent_id = None
    to_reopen = set()
    sibling_count = 0
    if parent_task_query:
        parent_task, _ = resolve_task_by_query(parent_task_query, all_tasks)
        if parent_task:
            parent_id = parent_task["id"]
            sibling_count = sum(1 for t in all_tasks if t.get("parent_id") == parent_id)
            # Android TaskRepository.kt:374-382: If added under a completed parent, reopen parent and ancestors
            if parent_task.get("is_completed"):
                to_reopen.add(parent_id)
                to_reopen.update(get_ancestor_ids(parent_id, all_tasks))
    else:
        sibling_count = sum(1 for t in all_tasks if not t.get("parent_id"))

    clean_priority = priority.strip().upper() if priority else "LOW"
    if clean_priority not in ("LOW", "MEDIUM", "HIGH", "URGENT"):
        clean_priority = "LOW"

    clean_repeat = repeat_type.strip().upper() if repeat_type else "NONE"
    section = determine_section(due_millis, False, eff_due_date)

    new_task = {
        "id": task_id,
        "title": title.strip(),
        "notes": notes.strip() if notes else None,
        "priority": clean_priority,
        "due_date": eff_due_date,
        "due_date_millis": due_millis,
        "due_time": eff_due_time,
        "location": location.strip() if location else None,
        "attachment_name": photos.strip() if photos else None,
        "is_completed": False,
        "section": section,
        "created_at": now_ms,
        "updated_at": now_ms,
        "alarm_tone_title": tone.strip() if tone else "Default Tone",
        "alarm_tone_uri": "DEFAULT" if (not tone or tone == "Default Tone") else tone.strip(),
        "repeat_type": clean_repeat,
        "repeat_days": repeat_days.strip() if repeat_days else None,
        "repeat_dates": repeat_dates.strip() if repeat_dates else None,
        "parent_id": parent_id,
        "user_code": user_code,
        "position": sibling_count
    }

    try:
        url = f"{SUPABASE_URL}/rest/v1/tasks"
        res = requests.post(url, headers=supabase_headers(), json=new_task, timeout=10)
        if res.status_code not in (200, 201):
            return f"ERROR: Failed to save task to database ({res.status_code} {res.text})"
    except Exception as e:
        return f"ERROR: Database write failed ({e})"

    # Reopen completed ancestors if needed (Android parity)
    if to_reopen:
        for pid in to_reopen:
            p_task = next((t for t in all_tasks if t["id"] == pid), None)
            if p_task:
                sec = determine_section(p_task.get("due_date_millis"), False, p_task.get("due_date", "Today"))
                patch_body = {
                    "is_completed": False,
                    "section": sec,
                    "completed_at": None,
                    "updated_at": now_ms
                }
                try:
                    requests.patch(f"{SUPABASE_URL}/rest/v1/tasks?id=eq.{pid}", headers=supabase_headers(), json=patch_body, timeout=10)
                    dispatch_alarm_wakeup({**p_task, **patch_body})
                except Exception as e:
                    logger.error(f"Error reopening ancestor {pid}: {e}")

    # Dispatch FCM wakeup
    wake_sent = dispatch_alarm_wakeup(new_task)
    wake_label = "Phone hardware alarm registered" if wake_sent else "Saved (token not configured)"

    date_time_label = f"{new_task['due_date']} at {new_task['due_time']}" if new_task['due_time'] else f"{new_task['due_date']} (All-day)"
    return json.dumps({
        "status": "SUCCESS",
        "task_name": new_task["title"],
        "notes": new_task["notes"] or "None",
        "repeat": new_task["repeat_type"] + (f" ({repeat_days})" if repeat_days else "") + (f" ({repeat_dates})" if repeat_dates else ""),
        "priority": new_task["priority"],
        "date_time": date_time_label,
        "tone": new_task["alarm_tone_title"] or "Default Tone",
        "location": new_task["location"] or "None",
        "photos": new_task["attachment_name"] or "None",
        "phone_alarm_status": wake_label
    })

def get_task_details(task_query: str) -> str:
    """Retrieves full details of a specific task using fuzzy matching if title is approximate."""
    all_tasks = fetch_all_tasks_for_user()
    task, candidates = resolve_task_by_query(task_query, all_tasks)
    if not task:
        if candidates:
            cand_names = [f"'{c.get('title')}'" for c in candidates[:3]]
            return f"Could not find exact task '{task_query}'. Did you mean one of these: {', '.join(cand_names)}?"
        return f"No task found matching '{task_query}'."

    subtasks = [t for t in all_tasks if t.get("parent_id") == task["id"]]
    subtask_titles = [s.get("title", "Untitled") for s in subtasks]

    info = {
        "id": task["id"],
        "title": task.get("title"),
        "notes": task.get("notes") or "None",
        "priority": task.get("priority", "LOW"),
        "due_date": task.get("due_date"),
        "due_time": task.get("due_time") or "None",
        "is_completed": task.get("is_completed", False),
        "section": task.get("section", "TODAY"),
        "repeat_type": task.get("repeat_type", "NONE"),
        "repeat_days": task.get("repeat_days") or "None",
        "repeat_dates": task.get("repeat_dates") or "None",
        "tone": task.get("alarm_tone_title") or "Default Tone",
        "location": task.get("location") or "None",
        "photos": task.get("attachment_name") or "None",
        "parent_id": task.get("parent_id") or "None",
        "subtasks_count": len(subtasks),
        "subtasks": subtask_titles
    }
    return json.dumps(info, indent=2)

def edit_task(
    task_query: str,
    new_title: str = None,
    new_due_date: str = None,
    new_due_time: str = None,
    new_priority: str = None,
    notes: str = None,
    new_repeat_type: str = None,
    new_repeat_days: str = None,
    new_repeat_dates: str = None,
    new_tone: str = None,
    new_location: str = None,
    is_completed: bool = None
) -> str:
    """Modifies properties of an existing task found via fuzzy matching. Rescheduling automatically reactivates completed tasks."""
    all_tasks = fetch_all_tasks_for_user()
    task, candidates = resolve_task_by_query(task_query, all_tasks)
    if not task:
        if candidates:
            return f"Could not find '{task_query}'. Possible matches: {', '.join(c.get('title') for c in candidates[:3])}"
        return f"No task found matching '{task_query}'."

    patch_data = {"updated_at": int(time.time() * 1000)}
    if new_title: patch_data["title"] = new_title.strip()
    if notes is not None: patch_data["notes"] = notes.strip() if notes else None
    if new_priority: patch_data["priority"] = new_priority.strip().upper()
    if new_due_date: patch_data["due_date"] = new_due_date.strip()
    if new_due_time: patch_data["due_time"] = new_due_time.strip()
    if new_repeat_type: patch_data["repeat_type"] = new_repeat_type.strip().upper()
    if new_repeat_days is not None: patch_data["repeat_days"] = new_repeat_days.strip() if new_repeat_days else None
    if new_repeat_dates is not None: patch_data["repeat_dates"] = new_repeat_dates.strip() if new_repeat_dates else None
    if new_tone: patch_data["alarm_tone_title"] = new_tone.strip()
    if new_location is not None: patch_data["location"] = new_location.strip() if new_location else None

    effective_date = new_due_date or task.get("due_date", "Today")
    effective_time = new_due_time or task.get("due_time")

    if is_completed is not None:
        patch_data["is_completed"] = is_completed
        patch_data["completed_at"] = int(time.time() * 1000) if is_completed else None
        if new_due_date or new_due_time:
            due_ms = compute_due_millis(effective_date, effective_time)
            patch_data["due_date_millis"] = due_ms
            patch_data["section"] = "COMPLETED" if is_completed else determine_section(due_ms, False, effective_date)
        else:
            current_ms = task.get("due_date_millis") or int(time.time() * 1000)
            patch_data["section"] = "COMPLETED" if is_completed else determine_section(current_ms, False, effective_date)
    elif new_due_date or new_due_time:
        # Rescheduling reactivates task if it was completed or overdue
        due_ms = compute_due_millis(effective_date, effective_time)
        patch_data["due_date_millis"] = due_ms
        patch_data["is_completed"] = False
        patch_data["completed_at"] = None
        patch_data["section"] = determine_section(due_ms, False, effective_date)

    task_id = task["id"]
    try:
        url = f"{SUPABASE_URL}/rest/v1/tasks?id=eq.{task_id}"
        res = requests.patch(url, headers=supabase_headers(), json=patch_data, timeout=10)
        if res.status_code not in (200, 204):
            return f"ERROR updating task: {res.text}"
    except Exception as e:
        return f"ERROR updating task: {e}"

    updated_task = {**task, **patch_data}
    dispatch_alarm_wakeup(updated_task)
    return f"Successfully updated task '{updated_task.get('title')}'. Hardware alarms synced."

def add_subtask(
    parent_task_query: str,
    title: str,
    due_date: str = None,
    due_time: str = None,
    priority: str = None,
    notes: str = None
) -> str:
    """
    Adds a subtask under an existing parent task (resolved via fuzzy match).
    Inherits parent's date (or 'Today'). If due_time is not provided, leaves it as None.
    If parent is completed, automatically reopens parent and all ancestors.
    """
    all_tasks = fetch_all_tasks_for_user()
    parent, candidates = resolve_task_by_query(parent_task_query, all_tasks)
    if not parent:
        if candidates:
            return f"Could not find parent task '{parent_task_query}'. Did you mean: {', '.join(c.get('title') for c in candidates[:3])}?"
        return f"Could not find parent task '{parent_task_query}'."

    effective_date = due_date.strip() if due_date else parent.get("due_date", "Today")
    effective_time = due_time.strip() if due_time else None
    effective_priority = priority.strip().upper() if priority else (parent.get("priority") or "LOW")

    return add_task(
        title=title,
        due_date=effective_date,
        due_time=effective_time,
        priority=effective_priority,
        notes=notes,
        parent_task_query=parent["title"]
    )

def adjust_parent(task_query: str, new_parent_query: str = None) -> str:
    """
    Changes or removes the parent for a subtask/task.
    If new_parent_query is None, 'none', or 'root', the task becomes a top-level task.
    Re-evaluates parent completion states matching TaskRepository.kt:500-502.
    """
    all_tasks = fetch_all_tasks_for_user()
    task, candidates = resolve_task_by_query(task_query, all_tasks)
    if not task:
        if candidates:
            return f"Could not find task '{task_query}'. Candidates: {', '.join(c.get('title') for c in candidates[:3])}"
        return f"Could not find task '{task_query}'."

    old_parent_id = task.get("parent_id")
    new_parent_id = None
    new_parent_title = "Root (No parent)"

    if new_parent_query and new_parent_query.strip().lower() not in ("none", "root", "remove", "null"):
        parent_task, p_candidates = resolve_task_by_query(new_parent_query, all_tasks)
        if not parent_task:
            return f"Could not find parent task '{new_parent_query}'."
        if parent_task["id"] == task["id"]:
            return "ERROR: A task cannot be its own parent."
        # Cycle detection
        descendants = get_descendant_ids(task["id"], all_tasks)
        if parent_task["id"] in descendants:
            return "ERROR: Cannot move task inside its own subtasks (cycle detected)."
        new_parent_id = parent_task["id"]
        new_parent_title = parent_task.get("title", "Parent")

    now_ms = int(time.time() * 1000)
    try:
        url = f"{SUPABASE_URL}/rest/v1/tasks?id=eq.{task['id']}"
        res = requests.patch(url, headers=supabase_headers(), json={"parent_id": new_parent_id, "updated_at": now_ms}, timeout=10)
        if res.status_code in (200, 204):
            task["parent_id"] = new_parent_id
            task["updated_at"] = now_ms

            # Recheck completions upward
            changed_parents = recheck_parent_completion(old_parent_id, all_tasks, now_ms)
            changed_parents += recheck_parent_completion(new_parent_id, all_tasks, now_ms)
            for cp in changed_parents:
                patch = {
                    "is_completed": cp["is_completed"],
                    "section": cp["section"],
                    "completed_at": cp.get("completed_at"),
                    "updated_at": now_ms
                }
                requests.patch(f"{SUPABASE_URL}/rest/v1/tasks?id=eq.{cp['id']}", headers=supabase_headers(), json=patch, timeout=10)
                dispatch_alarm_wakeup(cp)

            dispatch_alarm_wakeup(task)
            return f"Successfully updated parent of '{task.get('title')}' to: {new_parent_title}"
        return f"Database error updating parent: {res.text}"
    except Exception as e:
        return f"Error: {e}"

def delete_task(task_query: str) -> str:
    """Deletes a task and its descendants, canceling alarms and rechecking parent completion."""
    all_tasks = fetch_all_tasks_for_user()
    task, candidates = resolve_task_by_query(task_query, all_tasks)
    if not task:
        if candidates:
            return f"Could not find task '{task_query}'. Did you mean: {', '.join(c.get('title') for c in candidates[:3])}?"
        return f"No task found matching '{task_query}'."

    task_id = task["id"]
    old_parent_id = task.get("parent_id")
    descendant_ids = get_descendant_ids(task_id, all_tasks)
    all_to_delete = {task_id}.union(descendant_ids)

    try:
        for tid in all_to_delete:
            requests.delete(f"{SUPABASE_URL}/rest/v1/tasks?id=eq.{tid}", headers=supabase_headers(), timeout=10)

        # Recheck parent completion upward if subtask was removed
        if old_parent_id:
            now_ms = int(time.time() * 1000)
            remaining_tasks = [t for t in all_tasks if t["id"] not in all_to_delete]
            changed_parents = recheck_parent_completion(old_parent_id, remaining_tasks, now_ms)
            for cp in changed_parents:
                patch = {
                    "is_completed": cp["is_completed"],
                    "section": cp["section"],
                    "completed_at": cp.get("completed_at"),
                    "updated_at": now_ms
                }
                requests.patch(f"{SUPABASE_URL}/rest/v1/tasks?id=eq.{cp['id']}", headers=supabase_headers(), json=patch, timeout=10)
                dispatch_alarm_wakeup(cp)

        dispatch_alarm_wakeup()
        return f"🗑️ Deleted task '{task.get('title')}' ({len(all_to_delete)} task(s) removed) and canceled phone alarms."
    except Exception as e:
        return f"Error deleting task: {e}"

def set_task_completion(task_query: str, is_completed: bool) -> str:
    """
    Marks a task as completed or active again with bi-directional cascades matching TaskRepository.kt:152-240:
    - Completing: completes task + all recursive descendants; completes parent if all siblings are done.
    - Reopening: reopens task + all descendants + parent and all ancestors.
    """
    all_tasks = fetch_all_tasks_for_user()
    task, candidates = resolve_task_by_query(task_query, all_tasks)
    if not task:
        if candidates:
            return f"Could not find task '{task_query}'. Did you mean: {', '.join(c.get('title') for c in candidates[:3])}?"
        return f"No task found matching '{task_query}'."

    target_id = task["id"]
    now_ms = int(time.time() * 1000)
    by_id = {t["id"]: dict(t) for t in all_tasks}

    if is_completed:
        # 1. Target and all recursive descendants become COMPLETED
        to_complete = {target_id}
        to_complete.update(get_descendant_ids(target_id, all_tasks))

        for cid in to_complete:
            if cid in by_id:
                by_id[cid]["is_completed"] = True
                by_id[cid]["section"] = "COMPLETED"
                by_id[cid]["completed_at"] = now_ms
                by_id[cid]["updated_at"] = now_ms

        # 2. Cascade upward: parent becomes completed if ALL its direct children are completed
        curr_parent_id = task.get("parent_id")
        visited = set()
        while curr_parent_id and curr_parent_id not in visited:
            visited.add(curr_parent_id)
            parent_task = by_id.get(curr_parent_id)
            if not parent_task:
                break
            siblings = [t for t in by_id.values() if t.get("parent_id") == curr_parent_id]
            all_siblings_done = bool(siblings) and all(s.get("is_completed") or s["id"] in to_complete for s in siblings)
            if all_siblings_done:
                to_complete.add(curr_parent_id)
                parent_task["is_completed"] = True
                parent_task["section"] = "COMPLETED"
                parent_task["completed_at"] = now_ms
                parent_task["updated_at"] = now_ms
                curr_parent_id = parent_task.get("parent_id")
            else:
                break

        # Persist all completed
        for cid in to_complete:
            c_task = by_id[cid]
            patch_data = {
                "is_completed": True,
                "section": "COMPLETED",
                "completed_at": now_ms,
                "updated_at": now_ms
            }
            try:
                requests.patch(f"{SUPABASE_URL}/rest/v1/tasks?id=eq.{cid}", headers=supabase_headers(), json=patch_data, timeout=10)
                dispatch_alarm_wakeup(c_task)
            except Exception as e:
                logger.error(f"Error completing task {cid}: {e}")

        status_word = "completed 🎉"
        if len(to_complete) > 1:
            return f"Task '{task.get('title')}' and {len(to_complete) - 1} linked subtask(s)/parent marked as {status_word}."
        return f"Task '{task.get('title')}' marked as {status_word}."

    else:
        # Reopening (unticking)
        to_reopen = {target_id}
        # Rule 1: Unticking a parent -> all its subtasks become active
        to_reopen.update(get_descendant_ids(target_id, all_tasks))
        # Rule 2: Unticking a subtask -> parent & all ancestors become active
        to_reopen.update(get_ancestor_ids(target_id, all_tasks))

        for rid in to_reopen:
            r_task = by_id.get(rid)
            if r_task:
                due_ms = r_task.get("due_date_millis") or now_ms
                due_d = r_task.get("due_date", "Today")
                sec = determine_section(due_ms, False, due_d)
                patch_data = {
                    "is_completed": False,
                    "section": sec,
                    "completed_at": None,
                    "updated_at": now_ms
                }
                r_task.update(patch_data)
                try:
                    requests.patch(f"{SUPABASE_URL}/rest/v1/tasks?id=eq.{rid}", headers=supabase_headers(), json=patch_data, timeout=10)
                    dispatch_alarm_wakeup(r_task)
                except Exception as e:
                    logger.error(f"Error reopening task {rid}: {e}")

        status_word = "active again ⚡"
        if len(to_reopen) > 1:
            return f"Task '{task.get('title')}' and {len(to_reopen) - 1} linked ancestor(s)/subtask(s) marked as {status_word}."
        return f"Task '{task.get('title')}' marked as {status_word}."

def get_category_tasks(category: str) -> str:
    """
    Retrieves tasks by category: 'overdue', 'today', 'upcoming', 'completed', or 'all'.
    Matches Android sectioning: tasks on 'Today' are never overdue during today.
    """
    cat = category.strip().lower()
    tasks = fetch_all_tasks_for_user()
    now_dt = datetime.now(DEFAULT_TIMEZONE)
    today_start_dt = now_dt.replace(hour=0, minute=0, second=0, microsecond=0)
    today_start_ms = int(today_start_dt.timestamp() * 1000)
    today_end_ms = today_start_ms + 86400 * 1000

    filtered = []
    for t in tasks:
        completed = t.get("is_completed", False)
        due_ms = t.get("due_date_millis") or 0
        due_date_str = (t.get("due_date") or "").lower()

        if cat == "completed":
            if completed: filtered.append(t)
        elif cat == "overdue":
            if not completed:
                if "yesterday" in due_date_str:
                    filtered.append(t)
                elif due_ms > 0 and due_ms < today_start_ms and "today" not in due_date_str:
                    filtered.append(t)
        elif cat == "today":
            if not completed:
                if "today" in due_date_str:
                    filtered.append(t)
                elif due_ms >= today_start_ms and due_ms < today_end_ms:
                    filtered.append(t)
        elif cat == "upcoming":
            if not completed:
                if "tomorrow" in due_date_str or "upcoming" in due_date_str:
                    filtered.append(t)
                elif due_ms >= today_end_ms:
                    filtered.append(t)
        elif cat == "all":
            filtered.append(t)

    if not filtered:
        return f"No tasks found in category '{category}'."

    lines = [f"Found {len(filtered)} task(s) in '{category}':"]
    for idx, t in enumerate(filtered, 1):
        status = "✅" if t.get("is_completed") else "📌"
        lines.append(f"{idx}. {status} {t.get('title')} | Due: {t.get('due_date')} {t.get('due_time') or ''} | Priority: {t.get('priority', 'LOW')}")
    return "\n".join(lines)

def filter_tasks(
    status: str = "active",
    priority: str = None,
    date_filter: str = "all",
    start_date: str = None,
    end_date: str = None
) -> str:
    """
    Filters tasks.
    status: 'active' | 'completed' | 'all'
    priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT' | 'DUELESS'
    date_filter: 'today' | 'tomorrow' | 'range' | 'all'
    If date_filter is 'range', start_date and end_date (YYYY-MM-DD) are required.
    """
    tasks = fetch_all_tasks_for_user()
    res = []
    now_dt = datetime.now(DEFAULT_TIMEZONE)

    for t in tasks:
        completed = t.get("is_completed", False)
        if status == "active" and completed: continue
        if status == "completed" and not completed: continue

        t_prio = (t.get("priority") or "LOW").upper()
        if priority:
            clean_p = priority.strip().upper()
            if clean_p == "DUELESS":
                if t.get("due_date_millis") is not None: continue
            elif t_prio != clean_p:
                continue

        # Date filter
        due_ms = t.get("due_date_millis")
        if date_filter == "today":
            if not due_ms: continue
            due_dt = datetime.fromtimestamp(due_ms / 1000.0, DEFAULT_TIMEZONE)
            if due_dt.date() != now_dt.date(): continue
        elif date_filter == "tomorrow":
            if not due_ms: continue
            due_dt = datetime.fromtimestamp(due_ms / 1000.0, DEFAULT_TIMEZONE)
            if due_dt.date() != (now_dt.date() + timedelta(days=1)): continue
        elif date_filter == "range":
            if not start_date or not end_date:
                return "ERROR: When date_filter is 'range', start_date and end_date (YYYY-MM-DD) must be specified."
            if not due_ms: continue
            try:
                s_dt = datetime.strptime(start_date.strip(), "%Y-%m-%d").date()
                e_dt = datetime.strptime(end_date.strip(), "%Y-%m-%d").date()
                t_dt = datetime.fromtimestamp(due_ms / 1000.0, DEFAULT_TIMEZONE).date()
                if not (s_dt <= t_dt <= e_dt): continue
            except Exception as e:
                return f"ERROR parsing range dates: {e}"

        res.append(t)

    if not res:
        return "No tasks matched the specified filter criteria."

    lines = [f"Found {len(res)} matching task(s):"]
    for idx, t in enumerate(res, 1):
        lines.append(f"{idx}. {'✅' if t.get('is_completed') else '📌'} {t.get('title')} | Due: {t.get('due_date')} {t.get('due_time') or ''} | Priority: {t.get('priority', 'LOW')}")
    return "\n".join(lines)

def search_tasks(query: str) -> str:
    """Searches across all tasks by keyword matching title, notes, and location."""
    tasks = fetch_all_tasks_for_user()
    matches = []
    q = query.lower().strip()
    for t in tasks:
        title = (t.get("title") or "").lower()
        notes = (t.get("notes") or "").lower()
        loc = (t.get("location") or "").lower()
        if q in title or q in notes or q in loc or score_match(q, title) >= 0.6:
            matches.append(t)

    if not matches:
        return f"No tasks found matching query '{query}'."

    lines = [f"Search results for '{query}' ({len(matches)}):"]
    for idx, t in enumerate(matches, 1):
        lines.append(f"{idx}. {'✅' if t.get('is_completed') else '📌'} {t.get('title')} | Due: {t.get('due_date')} {t.get('due_time') or ''} | Notes: {t.get('notes') or '-'}")
    return "\n".join(lines)

def export_tasks_data(format_type: str = "csv", filter_status: str = "all", filter_category: str = "all") -> str:
    """
    Exports tasks to a downloadable file (CSV or JSON) and queues it for Telegram transmission.
    format_type: 'csv' | 'json'
    """
    tasks = fetch_all_tasks_for_user()
    filtered = []
    for t in tasks:
        if filter_status == "active" and t.get("is_completed"): continue
        if filter_status == "completed" and not t.get("is_completed"): continue
        filtered.append(t)

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = f"kairo_tasks_{timestamp}.{format_type.lower()}"
    file_path = EXPORTS_DIR / filename

    if format_type.lower() == "json":
        with open(file_path, "w", encoding="utf-8") as f:
            json.dump(filtered, f, indent=2)
    else:
        fieldnames = ["id", "title", "notes", "priority", "due_date", "due_time", "is_completed", "section", "repeat_type", "location", "created_at"]
        with open(file_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames, extrasaction="ignore")
            writer.writeheader()
            for t in filtered:
                writer.writerow(t)

    # Register in outbox for Telegram bot to deliver document
    outbox.append(str(file_path))
    return f"Tasks exported successfully ({len(filtered)} tasks). The file '{filename}' has been prepared and is being sent to your Telegram."

# Export tool list for Gemini API
TOOLS = [
    get_recent_tones,
    add_task,
    get_task_details,
    edit_task,
    add_subtask,
    adjust_parent,
    delete_task,
    set_task_completion,
    get_category_tasks,
    filter_tasks,
    search_tasks,
    export_tasks_data
]
