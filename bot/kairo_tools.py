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
    "fcm_token": DEFAULT_FCM_TOKEN or None,
    "chat_id": None
}

def set_context(user_code: str, fcm_token: str = None, chat_id: int = None):
    current_user["user_code"] = user_code
    current_user["fcm_token"] = fcm_token or DEFAULT_FCM_TOKEN or None
    if chat_id is not None:
        current_user["chat_id"] = chat_id

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
            if task.get("location"):
                data_payload["location"] = str(task["location"])
            if task.get("latitude") is not None:
                data_payload["latitude"] = str(task["latitude"])
                data_payload["lat"] = str(task["latitude"])
            if task.get("longitude") is not None:
                data_payload["longitude"] = str(task["longitude"])
                data_payload["lng"] = str(task["longitude"])
            radius = task.get("radius_meters") or task.get("location_radius")
            if radius:
                data_payload["locationRadius"] = str(radius)
            if task.get("snooze_until_millis"):
                data_payload["snoozeUntilMillis"] = str(task["snooze_until_millis"])

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

    # Substring check with length penalty
    if q in t or t in q:
        len_ratio = min(len(q), len(t)) / max(len(q), len(t))
        return 0.80 + 0.18 * len_ratio

    qw = [w for w in re.findall(r'\w+', q) if w not in ('task', 'habit', 'the', 'a', 'an', 'to', 'for', 'my')]
    tw = [w for w in re.findall(r'\w+', t) if w not in ('task', 'habit', 'the', 'a', 'an', 'to', 'for', 'my')]
    if not qw or not tw:
        return SequenceMatcher(None, q, t).ratio()

    # Stem / fuzzy token matches
    stem_hits = 0
    matched_tw = set()
    for w1 in qw:
        for w2 in tw:
            if w2 in matched_tw:
                continue
            if w1 == w2 or (len(w1) >= 4 and len(w2) >= 4 and (w1 in w2 or w2 in w1)):
                stem_hits += 1
                matched_tw.add(w2)
                break
            elif SequenceMatcher(None, w1, w2).ratio() >= 0.80:
                stem_hits += 1
                matched_tw.add(w2)
                break

    unmatched_qw = len(qw) - stem_hits
    unmatched_tw = len(tw) - stem_hits

    dice = (2.0 * stem_hits) / (len(qw) + len(tw))
    seq_ratio = SequenceMatcher(None, q, t).ratio()

    # Heavy penalty if distinct conflicting words (e.g. 'drinking milk' vs 'drink water')
    if unmatched_qw > 0 and unmatched_tw > 0 and stem_hits == 1 and (len(qw) > 1 or len(tw) > 1):
        return min(dice * 0.6, seq_ratio)

    return max(dice, seq_ratio)

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
    high_matches = [t for s, t in scored if s >= 0.70]

    if scored and scored[0][0] >= 0.70:
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
    return ancestors

def get_task_path(task_id: str, all_tasks: list) -> str:
    """Returns breadcrumb path string e.g. 'Project > Subtask' matching Android getTaskPath."""
    by_id = {t["id"]: t for t in all_tasks}
    chain = []
    curr = by_id.get(task_id)
    visited = set()
    while curr and curr["id"] not in visited:
        visited.add(curr["id"])
        chain.insert(0, curr.get("title", "Untitled"))
        curr = by_id.get(curr.get("parent_id")) if curr.get("parent_id") else None
    return " > ".join(chain)

def get_subtask_progress(task_id: str, all_tasks: list) -> str:
    """Returns 'X/Y completed' progress string matching Android getSubtaskProgress."""
    direct = [t for t in all_tasks if t.get("parent_id") == task_id]
    if not direct:
        return "No subtasks"
    done_count = sum(1 for t in direct if t.get("is_completed"))
    return f"{done_count}/{len(direct)} completed"

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

def normalize_date_string(date_str: str) -> str:
    """
    Standardizes any date input to an actual formatted date string (e.g. 'Oct 11').
    Ensures relative words ('Today', 'Tomorrow', 'Yesterday') are NEVER stored in the database.
    Preserves 'SNOOZED'.
    """
    if not date_str or not date_str.strip():
        now_dt = datetime.now(DEFAULT_TIMEZONE)
        return now_dt.strftime("%b %d")

    clean = date_str.strip()
    if clean.upper() == "SNOOZED":
        return "SNOOZED"

    now_dt = datetime.now(DEFAULT_TIMEZONE)
    low = clean.lower()
    if "today" in low:
        return now_dt.strftime("%b %d")
    elif "tomorrow" in low:
        return (now_dt + timedelta(days=1)).strftime("%b %d")
    elif "yesterday" in low:
        return (now_dt - timedelta(days=1)).strftime("%b %d")

    for fmt in ("%Y-%m-%d", "%b %d", "%B %d", "%d/%m/%Y", "%m/%d/%Y", "%b %d, %Y", "%d %b %Y"):
        try:
            parsed = datetime.strptime(clean, fmt)
            if parsed.year <= 1900:
                parsed = parsed.replace(year=now_dt.year)
            return parsed.strftime("%b %d")
        except ValueError:
            pass

    return clean

def determine_section(due_millis: int, is_completed: bool, due_date_str: str = "", due_time_str: str = None) -> str:
    """
    Determines section matching Android TaskRepository.kt and TaskViewModel.kt:
    - If completed: COMPLETED
    - If due_date_str == 'SNOOZED': SNOOZED
    - If due_date_str contains 'Yesterday': OVERDUE
    - If due_millis < today_start_ms: OVERDUE
    - If on today (today_start_ms <= due_millis < today_end_ms):
        - If a specific time was set and that time has passed (due_millis < now_ms): OVERDUE
        - Otherwise: TODAY
    - Else: UPCOMING
    """
    if is_completed:
        return "COMPLETED"

    clean_date = (due_date_str or "").strip().lower()
    if clean_date == "snoozed":
        return "SNOOZED"
    if "yesterday" in clean_date:
        return "OVERDUE"

    now_dt = datetime.now(DEFAULT_TIMEZONE)
    now_ms = int(now_dt.timestamp() * 1000)
    today_start_dt = now_dt.replace(hour=0, minute=0, second=0, microsecond=0)
    today_start_ms = int(today_start_dt.timestamp() * 1000)
    today_end_ms = today_start_ms + 86400 * 1000

    if due_millis is not None and due_millis > 0:
        if due_millis < now_ms:
            return "OVERDUE"
        elif due_millis < today_end_ms:
            return "TODAY"
        else:
            return "UPCOMING"

    return "OVERDUE"

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
    due_date: str,
    due_time: str,
    priority: str = "LOW",
    notes: str = None,
    repeat_type: str = "NONE",
    repeat_days: str = None,
    repeat_dates: str = None,
    tone: str = "Default Tone",
    location: str = None,
    photos: str = None,
    parent_task_query: str = None,
    latitude: float = None,
    longitude: float = None,
    location_radius: int = 500
) -> str:
    """
    Creates a new task in Kairo. Matches Android TaskRepository.addTask.
    Title, due_date, and due_time are STRICTLY MANDATORY fields for alarm scheduling.
    If added under a completed parent, reopens parent and all ancestors.
    Supports geofence location coordinates (latitude, longitude, location_radius).
    """
    if not title or not title.strip():
        return "ERROR: Task name/title is mandatory."
    if not due_date or not due_date.strip():
        return "ERROR: Due Date is mandatory. Please ask the user for the date before creating the task."
    if not due_time or not due_time.strip():
        return "ERROR: Due Time is mandatory for alarms. Please ask the user for the time before creating the task."

    user_code = current_user["user_code"]
    task_id = str(uuid.uuid4())
    eff_due_date = normalize_date_string(due_date)
    eff_due_time = due_time.strip()
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
    section = determine_section(due_millis, False, eff_due_date, eff_due_time)

    new_task = {
        "id": task_id,
        "title": title.strip(),
        "notes": notes.strip() if notes else None,
        "priority": clean_priority,
        "due_date": eff_due_date,
        "due_date_millis": due_millis,
        "due_time": eff_due_time,
        "location": location.strip() if location else None,
        "latitude": latitude,
        "longitude": longitude,
        "radius_meters": location_radius,
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
    """
    Retrieves full details of a specific task using fuzzy matching.
    Includes breadcrumbs path, subtask progress count, location coordinates, and alarm info.
    """
    all_tasks = fetch_all_tasks_for_user()
    task, candidates = resolve_task_by_query(task_query, all_tasks)
    if not task:
        if candidates:
            cand_names = [f"'{c.get('title')}'" for c in candidates[:3]]
            return f"Could not find exact task '{task_query}'. Did you mean one of these: {', '.join(cand_names)}?"
        return f"No task found matching '{task_query}'."

    subtasks = [t for t in all_tasks if t.get("parent_id") == task["id"]]
    subtask_titles = [s.get("title", "Untitled") for s in subtasks]
    breadcrumb_path = get_task_path(task["id"], all_tasks)
    progress_str = get_subtask_progress(task["id"], all_tasks)

    snooze_str = "None"
    if task.get("snooze_until_millis"):
        snooze_dt = datetime.fromtimestamp(task["snooze_until_millis"] / 1000.0, DEFAULT_TIMEZONE)
        snooze_str = snooze_dt.strftime("%Y-%m-%d %I:%M %p")

    info = {
        "id": task["id"],
        "title": task.get("title"),
        "path": breadcrumb_path,
        "notes": task.get("notes") or "None",
        "priority": task.get("priority", "LOW"),
        "due_date": task.get("due_date"),
        "due_time": task.get("due_time") or "None",
        "is_completed": task.get("is_completed", False),
        "section": task.get("section", "TODAY"),
        "snoozed_until": snooze_str,
        "repeat_type": task.get("repeat_type", "NONE"),
        "repeat_days": task.get("repeat_days") or "None",
        "repeat_dates": task.get("repeat_dates") or "None",
        "tone": task.get("alarm_tone_title") or "Default Tone",
        "location": task.get("location") or "None",
        "latitude": task.get("latitude"),
        "longitude": task.get("longitude"),
        "location_radius": task.get("radius_meters") or task.get("location_radius", 500),
        "photos": task.get("attachment_name") or "None",
        "parent_id": task.get("parent_id") or "None",
        "subtasks_count": len(subtasks),
        "subtask_progress": progress_str,
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
    new_latitude: float = None,
    new_longitude: float = None,
    new_location_radius: int = None,
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
    if new_due_date: patch_data["due_date"] = normalize_date_string(new_due_date)
    if new_due_time: patch_data["due_time"] = new_due_time.strip()
    if new_repeat_type: patch_data["repeat_type"] = new_repeat_type.strip().upper()
    if new_repeat_days is not None: patch_data["repeat_days"] = new_repeat_days.strip() if new_repeat_days else None
    if new_repeat_dates is not None: patch_data["repeat_dates"] = new_repeat_dates.strip() if new_repeat_dates else None
    if new_tone: patch_data["alarm_tone_title"] = new_tone.strip()
    if new_location is not None: patch_data["location"] = new_location.strip() if new_location else None
    if new_latitude is not None: patch_data["latitude"] = new_latitude
    if new_longitude is not None: patch_data["longitude"] = new_longitude
    if new_location_radius is not None: patch_data["radius_meters"] = new_location_radius

    effective_date = normalize_date_string(new_due_date) if new_due_date else normalize_date_string(task.get("due_date", "Today"))
    effective_time = new_due_time or task.get("due_time")

    if is_completed is not None:
        patch_data["is_completed"] = is_completed
        patch_data["completed_at"] = int(time.time() * 1000) if is_completed else None
        if new_due_date or new_due_time:
            due_ms = compute_due_millis(effective_date, effective_time)
            patch_data["due_date_millis"] = due_ms
            patch_data["section"] = "COMPLETED" if is_completed else determine_section(due_ms, False, effective_date, effective_time)
        else:
            current_ms = task.get("due_date_millis") or int(time.time() * 1000)
            patch_data["section"] = "COMPLETED" if is_completed else determine_section(current_ms, False, effective_date, effective_time)
    elif new_due_date or new_due_time:
        # Rescheduling reactivates task if it was completed or overdue
        due_ms = compute_due_millis(effective_date, effective_time)
        patch_data["due_date_millis"] = due_ms
        patch_data["is_completed"] = False
        patch_data["completed_at"] = None
        patch_data["section"] = determine_section(due_ms, False, effective_date, effective_time)

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

def snooze_task(task_query: str, minutes: int = 15) -> str:
    """
    Snoozes an active task for a specified duration in minutes (default 15 minutes).
    Matches Android TaskRepository.snoozeTask.
    Sets due_date to 'SNOOZED' and section to 'SNOOZED'.
    """
    all_tasks = fetch_all_tasks_for_user()
    task, candidates = resolve_task_by_query(task_query, all_tasks)
    if not task:
        if candidates:
            return f"Could not find task '{task_query}'. Did you mean: {', '.join(c.get('title') for c in candidates[:3])}?"
        return f"No task found matching '{task_query}'."

    task_id = task["id"]
    now_ms = int(time.time() * 1000)
    snooze_until_ms = now_ms + (minutes * 60 * 1000)
    snooze_time_str = datetime.fromtimestamp(snooze_until_ms / 1000.0, DEFAULT_TIMEZONE).strftime("%I:%M %p")

    patch_data = {
        "due_date": "SNOOZED",
        "snooze_until_millis": snooze_until_ms,
        "due_date_millis": snooze_until_ms,
        "section": "SNOOZED",
        "is_completed": False,
        "updated_at": now_ms
    }

    try:
        url = f"{SUPABASE_URL}/rest/v1/tasks?id=eq.{task_id}"
        res = requests.patch(url, headers=supabase_headers(), json=patch_data, timeout=10)
        if res.status_code in (200, 204):
            updated_task = {**task, **patch_data}
            dispatch_alarm_wakeup(updated_task)
            return f"💤 Task '{task.get('title')}' snoozed for {minutes} minutes (until {snooze_time_str}). Hardware alarm updated on your phone."
        return f"Database error snoozing task: {res.text}"
    except Exception as e:
        return f"Error snoozing task: {e}"

def reschedule_task(task_query: str, new_due_date: str, new_due_time: str) -> str:
    """
    Reschedules a task to a new date and/or time, reactivating it if completed or overdue.
    Matches Android task reschedule behavior.
    """
    if not new_due_date or not new_due_date.strip():
        return "ERROR: New due date is mandatory for rescheduling."
    if not new_due_time or not new_due_time.strip():
        return "ERROR: New due time is mandatory for rescheduling."
    return edit_task(
        task_query=task_query,
        new_due_date=new_due_date,
        new_due_time=new_due_time,
        is_completed=False
    )

def add_subtask(
    parent_task_query: str,
    title: str,
    due_date: str,
    due_time: str,
    priority: str = None,
    notes: str = None
) -> str:
    """
    Adds a subtask under an existing parent task (resolved via fuzzy match).
    Title, due_date, and due_time are STRICTLY MANDATORY fields for alarm scheduling.
    If parent is completed, automatically reopens parent and all ancestors.
    """
    if not parent_task_query or not parent_task_query.strip():
        return "ERROR: Parent task name/query is mandatory."
    if not title or not title.strip():
        return "ERROR: Subtask title is mandatory."
    if not due_date or not due_date.strip():
        return "ERROR: Due Date is mandatory. Please ask the user for the date before creating the subtask."
    if not due_time or not due_time.strip():
        return "ERROR: Due Time is mandatory for alarms. Please ask the user for the time before creating the subtask."

    all_tasks = fetch_all_tasks_for_user()
    parent, candidates = resolve_task_by_query(parent_task_query, all_tasks)
    if not parent:
        if candidates:
            return f"Could not find parent task '{parent_task_query}'. Did you mean: {', '.join(c.get('title') for c in candidates[:3])}?"
        return f"Could not find parent task '{parent_task_query}'."

    eff_due_date = normalize_date_string(due_date)
    eff_due_time = due_time.strip()
    effective_priority = priority.strip().upper() if priority else (parent.get("priority") or "LOW")

    return add_task(
        title=title,
        due_date=eff_due_date,
        due_time=eff_due_time,
        priority=effective_priority,
        notes=notes,
        parent_task_query=parent["title"]
    )

def adjust_parent(task_query: str, new_parent_query: str = None, move_subtasks: bool = True) -> str:
    """
    Changes or removes the parent for a subtask/task.
    If new_parent_query is None, 'none', or 'root', the task becomes a top-level task.
    move_subtasks: If False, direct children remain under the old parent (matches Android TaskRepository.moveTask).
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
        # If move_subtasks is False, direct children are detached to old_parent_id
        if not move_subtasks:
            direct_children = [t for t in all_tasks if t.get("parent_id") == task["id"]]
            for child in direct_children:
                requests.patch(
                    f"{SUPABASE_URL}/rest/v1/tasks?id=eq.{child['id']}",
                    headers=supabase_headers(),
                    json={"parent_id": old_parent_id, "updated_at": now_ms},
                    timeout=10
                )

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

def delete_task(task_query: str, delete_subtasks: bool = True) -> str:
    """
    Deletes a task. Matches Android TaskRepository.deleteTaskWithSubtasks:
    If delete_subtasks is False, child subtasks are reparented to the deleted task's parent.
    If delete_subtasks is True, all descendants are also deleted.
    """
    all_tasks = fetch_all_tasks_for_user()
    task, candidates = resolve_task_by_query(task_query, all_tasks)
    if not task:
        if candidates:
            return f"Could not find task '{task_query}'. Did you mean: {', '.join(c.get('title') for c in candidates[:3])}?"
        return f"No task found matching '{task_query}'."

    task_id = task["id"]
    old_parent_id = task.get("parent_id")
    now_ms = int(time.time() * 1000)

    try:
        if not delete_subtasks:
            # Lift direct children to old_parent_id
            direct_children = [t for t in all_tasks if t.get("parent_id") == task_id]
            for child in direct_children:
                requests.patch(
                    f"{SUPABASE_URL}/rest/v1/tasks?id=eq.{child['id']}",
                    headers=supabase_headers(),
                    json={"parent_id": old_parent_id, "updated_at": now_ms},
                    timeout=10
                )
            all_to_delete = {task_id}
        else:
            descendant_ids = get_descendant_ids(task_id, all_tasks)
            all_to_delete = {task_id}.union(descendant_ids)

        for tid in all_to_delete:
            requests.delete(f"{SUPABASE_URL}/rest/v1/tasks?id=eq.{tid}", headers=supabase_headers(), timeout=10)

        # Recheck parent completion upward if subtask was removed
        if old_parent_id:
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

def bulk_complete_tasks(category: str = None, task_queries: list = None) -> str:
    """
    Marks multiple tasks as completed at once. Matches Android bulk completion.
    category: 'overdue' | 'today' | 'upcoming' | 'all'
    task_queries: list of task title queries to complete
    """
    all_tasks = fetch_all_tasks_for_user()
    targets = []

    if category:
        cat = category.strip().lower()
        now_dt = datetime.now(DEFAULT_TIMEZONE)
        today_start_dt = now_dt.replace(hour=0, minute=0, second=0, microsecond=0)
        today_start_ms = int(today_start_dt.timestamp() * 1000)
        today_end_ms = today_start_ms + 86400 * 1000

        for t in all_tasks:
            if t.get("is_completed"): continue
            due_ms = t.get("due_date_millis") or 0
            due_d = (t.get("due_date") or "").lower()

            if cat == "overdue":
                if "yesterday" in due_d or (due_ms > 0 and due_ms < today_start_ms and "today" not in due_d):
                    targets.append(t)
            elif cat == "today":
                if "today" in due_d or (today_start_ms <= due_ms < today_end_ms):
                    targets.append(t)
            elif cat == "upcoming":
                if "tomorrow" in due_d or due_ms >= today_end_ms:
                    targets.append(t)
            elif cat == "all":
                targets.append(t)
    elif task_queries:
        for q in task_queries:
            matched, _ = resolve_task_by_query(q, all_tasks)
            if matched and matched not in targets:
                targets.append(matched)

    if not targets:
        return "No active tasks found matching the criteria to complete."

    completed_titles = []
    for t in targets:
        set_task_completion(t["title"], is_completed=True)
        completed_titles.append(t["title"])

    return f"✅ Successfully completed {len(completed_titles)} task(s): {', '.join(completed_titles[:5])}{'...' if len(completed_titles) > 5 else ''}."

def bulk_delete_tasks(category: str = None, task_queries: list = None) -> str:
    """
    Deletes multiple tasks at once. Matches Android TaskRepository.deleteTasks.
    category: 'completed' | 'overdue' | 'all'
    task_queries: list of task title queries to delete
    """
    all_tasks = fetch_all_tasks_for_user()
    targets = []

    if category:
        cat = category.strip().lower()
        for t in all_tasks:
            if cat == "completed" and t.get("is_completed"):
                targets.append(t)
            elif cat == "overdue" and not t.get("is_completed") and t.get("section") == "OVERDUE":
                targets.append(t)
            elif cat == "all":
                targets.append(t)
    elif task_queries:
        for q in task_queries:
            matched, _ = resolve_task_by_query(q, all_tasks)
            if matched and matched not in targets:
                targets.append(matched)

    if not targets:
        return "No tasks found matching the criteria to delete."

    count = len(targets)
    for t in targets:
        delete_task(t["title"], delete_subtasks=True)

    return f"🗑️ Successfully deleted {count} task(s) and canceled phone alarms."

def clear_completed_tasks() -> str:
    """
    Deletes all completed tasks from the workspace. Matches Android cleanup.
    """
    return bulk_delete_tasks(category="completed")

def reorder_subtasks(parent_task_query: str, ordered_subtask_titles: list) -> str:
    """
    Reorders direct subtasks under a parent task matching Android TaskRepository.reorderTasks.
    """
    all_tasks = fetch_all_tasks_for_user()
    parent, _ = resolve_task_by_query(parent_task_query, all_tasks)
    if not parent:
        return f"Could not find parent task '{parent_task_query}'."

    subtasks = [t for t in all_tasks if t.get("parent_id") == parent["id"]]
    if not subtasks:
        return f"No subtasks exist under parent '{parent.get('title')}'."

    now_ms = int(time.time() * 1000)
    updated_count = 0
    for idx, title_query in enumerate(ordered_subtask_titles):
        matched, _ = resolve_task_by_query(title_query, subtasks)
        if matched:
            requests.patch(
                f"{SUPABASE_URL}/rest/v1/tasks?id=eq.{matched['id']}",
                headers=supabase_headers(),
                json={"position": idx, "updated_at": now_ms},
                timeout=10
            )
            updated_count += 1

    dispatch_alarm_wakeup(parent)
    return f"↕️ Successfully reordered {updated_count} subtask(s) under '{parent.get('title')}'."

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

# ==========================================
# HABIT DATA ACCESS & PROTOCOLS
# ==========================================
DAY_NAME_TO_INT = {
    "mon": 1, "monday": 1,
    "tue": 2, "tues": 2, "tuesday": 2,
    "wed": 3, "wednesday": 3,
    "thu": 4, "thur": 4, "thurs": 4, "thursday": 4,
    "fri": 5, "friday": 5,
    "sat": 6, "saturday": 6,
    "sun": 7, "sunday": 7,
}
INT_TO_DAY_NAME = {
    1: "Mon", 2: "Tue", 3: "Wed", 4: "Thu", 5: "Fri", 6: "Sat", 7: "Sun"
}

def parse_repeat_days(days_input) -> str:
    """Parses various day representations into a sorted comma-separated string like '1,3,5'."""
    if not days_input:
        return "1,2,3,4,5,6,7"
    if isinstance(days_input, list):
        days_str = ",".join(str(x) for x in days_input)
    else:
        days_str = str(days_input)

    days_str_lower = days_str.lower().strip()
    if "everyday" in days_str_lower or "daily" in days_str_lower:
        return "1,2,3,4,5,6,7"
    if "weekday" in days_str_lower:
        return "1,2,3,4,5"
    if "weekend" in days_str_lower:
        return "6,7"

    parts = re.split(r"[,;\s]+", days_str_lower)
    result_ints = set()
    for p in parts:
        p = p.strip()
        if not p:
            continue
        if p.isdigit():
            val = int(p)
            if 1 <= val <= 7:
                result_ints.add(val)
        elif p in DAY_NAME_TO_INT:
            result_ints.add(DAY_NAME_TO_INT[p])

    if not result_ints:
        return "1,2,3,4,5,6,7"
    return ",".join(str(x) for x in sorted(result_ints))

def format_repeat_schedule(habit: dict) -> str:
    """Formats repeat schedule into user-friendly text."""
    repeat_type = (habit.get("repeat_type") or "EVERYDAY").upper()
    if repeat_type == "EVERYDAY":
        return "everyday"
    elif repeat_type == "WEEKLY_X":
        count = habit.get("weekly_target_count") or 3
        return f"weekly {count} times"
    elif repeat_type == "SPECIFIC_DAYS":
        raw_days = str(habit.get("repeat_days") or "1,2,3,4,5,6,7")
        day_nums = [int(x.strip()) for x in raw_days.split(",") if x.strip().isdigit()]
        day_names = [INT_TO_DAY_NAME.get(d, str(d)) for d in sorted(day_nums)]
        return f"specific days ({', '.join(day_names)})"
    return "everyday"

def parse_increment_notes(raw_note: str | None) -> dict[int, str]:
    """Parses 1-indexed target count notes stored as JSON: '{\"1\":\"Fajr\", \"3\":\"Asr\"}'."""
    if not raw_note:
        return {}
    trimmed = raw_note.strip()
    if trimmed.startswith("{") and trimmed.endswith("}"):
        try:
            data = json.loads(trimmed)
            res = {}
            for k, v in data.items():
                if str(k).isdigit() and int(k) > 0 and str(v).strip():
                    res[int(k)] = str(v).strip()
            return dict(sorted(res.items()))
        except Exception:
            pass
    parts = [p.strip() for p in re.split(r"[,;\n]+", trimmed) if p.strip()]
    res = {}
    for idx, p in enumerate(parts, 1):
        res[idx] = p
    return res

def serialize_increment_notes(notes_dict: dict[int, str]) -> str | None:
    """Serializes 1-indexed count notes to compact JSON."""
    filtered = {k: str(v).strip() for k, v in sorted(notes_dict.items()) if k > 0 and str(v).strip()}
    if not filtered:
        return None
    return json.dumps({str(k): v for k, v in filtered.items()})

def format_display_note(raw_note: str | None) -> str:
    """Formats raw notes (JSON or text) for clean user display."""
    if not raw_note:
        return "None"
    trimmed = raw_note.strip()
    if trimmed.startswith("{") and trimmed.endswith("}"):
        m = parse_increment_notes(trimmed)
        if not m:
            return "None"
        return ", ".join(f"#{k}: {v}" for k, v in m.items())
    return trimmed

def is_rest_day(habit: dict, target_date) -> bool:
    """Determines if target_date is a rest day for a SPECIFIC_DAYS habit."""
    repeat_type = (habit.get("repeat_type") or "EVERYDAY").upper()
    if repeat_type != "SPECIFIC_DAYS":
        return False
    raw_days = str(habit.get("repeat_days") or "1,2,3,4,5,6,7")
    allowed_days = [int(x.strip()) for x in raw_days.split(",") if x.strip().isdigit()]
    day_num = target_date.weekday() + 1
    return day_num not in allowed_days

def resolve_habit_date(date_str: str = "today"):
    """
    Resolves date_str relative to Asia/Kolkata timezone.
    Returns (date_obj, date_iso_string 'YYYY-MM-DD', day_name 'Wednesday').
    """
    now = datetime.now(DEFAULT_TIMEZONE)
    today = now.date()

    if not date_str:
        return today, today.strftime("%Y-%m-%d"), today.strftime("%A")

    s = date_str.strip().lower()
    if s in ("today", "todays"):
        d = today
    elif s in ("yesterday", "yesterdays"):
        d = today - timedelta(days=1)
    elif s in ("tomorrow", "tomorrows"):
        d = today + timedelta(days=1)
    elif s in DAY_NAME_TO_INT:
        target_dow = DAY_NAME_TO_INT[s]
        today_dow = today.weekday() + 1
        diff = (today_dow - target_dow) % 7
        d = today - timedelta(days=diff)
    else:
        d = None
        for fmt in ("%Y-%m-%d", "%d-%m-%Y", "%d/%m/%Y", "%Y/%m/%d", "%b %d, %Y", "%b %d %Y", "%d %b %Y", "%d %B %Y", "%B %d, %Y", "%b %d", "%d %b", "%B %d", "%d %B"):
            try:
                parsed = datetime.strptime(s, fmt)
                if "%Y" not in fmt and "%y" not in fmt:
                    parsed = parsed.replace(year=today.year)
                d = parsed.date()
                break
            except Exception:
                pass
        
        if d is None:
            clean_s = re.sub(r'(\d+)(st|nd|rd|th)', r'\1', s)
            for fmt in ("%d %b", "%d %B", "%b %d", "%B %d", "%d %b %Y", "%d %B %Y", "%b %d %Y", "%B %d %Y", "%d %b, %Y", "%b %d, %Y"):
                try:
                    parsed = datetime.strptime(clean_s, fmt)
                    if "%Y" not in fmt and "%y" not in fmt:
                        parsed = parsed.replace(year=today.year)
                    d = parsed.date()
                    break
                except Exception:
                    pass

        if d is None:
            d = today

    return d, d.strftime("%Y-%m-%d"), d.strftime("%A")

def fetch_all_habits_for_user() -> list:
    """Fetches all habits for the active user from Supabase."""
    user_code = current_user.get("user_code", "")
    if not user_code:
        return []
    try:
        url = f"{SUPABASE_URL}/rest/v1/habits?user_code=eq.{user_code}&select=*&order=position.asc,created_at.desc"
        res = requests.get(url, headers=supabase_headers(), timeout=10)
        if res.status_code == 200:
            return res.json()
    except Exception as e:
        logger.error(f"Error fetching habits: {e}")
    return []

def fetch_all_habit_logs_for_user(habit_id: str = None) -> list:
    """Fetches all habit logs for the active user from Supabase."""
    user_code = current_user.get("user_code", "")
    if not user_code:
        return []
    try:
        url = f"{SUPABASE_URL}/rest/v1/habit_logs?user_code=eq.{user_code}&select=*"
        if habit_id:
            url += f"&habit_id=eq.{habit_id}"
        res = requests.get(url, headers=supabase_headers(), timeout=10)
        if res.status_code == 200:
            return res.json()
    except Exception as e:
        logger.error(f"Error fetching habit logs: {e}")
    return []

def resolve_habit_by_query(query: str, habits: list = None) -> tuple[dict | None, list]:
    """Resolves habit name using exact match and fuzzy stem scoring."""
    if habits is None:
        habits = fetch_all_habits_for_user()
    if not habits:
        return None, []

    clean_query = query.strip()
    for h in habits:
        if h.get("id") == clean_query:
            return h, [h]

    scored = []
    for h in habits:
        title = h.get("title", "")
        s_title = score_match(clean_query, title)
        scored.append((s_title, h))

    scored.sort(key=lambda x: x[0], reverse=True)
    high_matches = [h for s, h in scored if s >= 0.70]

    if scored and scored[0][0] >= 0.70:
        return scored[0][1], high_matches
    return None, [h for s, h in scored[:3]]

def is_day_complete(habit: dict, record: dict | None) -> bool:
    """Returns True if record represents a completed day."""
    if not record:
        return False
    state = record.get("state")
    if state == "DONE":
        return True
    h_type = (habit.get("type") or "YES_NO").upper()
    tgt_val = habit.get("target_value") or 1
    val = record.get("value") or 0
    if h_type == "TARGET" and val >= tgt_val and tgt_val > 0:
        return True
    return False

def count_weekly_completions(habit: dict, logs_by_date: dict, target_date) -> int:
    """Counts completed days in the Monday-Sunday week containing target_date."""
    monday = target_date - timedelta(days=target_date.weekday())
    count = 0
    for offset in range(7):
        d = monday + timedelta(days=offset)
        d_str = d.strftime("%Y-%m-%d")
        if is_day_complete(habit, logs_by_date.get(d_str)):
            count += 1
    return count

def get_habit_start_date(habit: dict):
    """Retrieves effective start date of habit."""
    raw = habit.get("start_date") or ""
    if raw.strip():
        try:
            return datetime.strptime(raw.strip(), "%Y-%m-%d").date()
        except Exception:
            pass
    created_at = habit.get("created_at")
    if created_at:
        try:
            return datetime.fromtimestamp(created_at / 1000.0, DEFAULT_TIMEZONE).date()
        except Exception:
            pass
    return datetime.now(DEFAULT_TIMEZONE).date()

def calculate_habit_streak(habit: dict, logs_by_date: dict, today) -> int:
    """Calculates habit streak matching Android HabitRepository logic."""
    repeat_type = (habit.get("repeat_type") or "EVERYDAY").upper()

    if repeat_type == "EVERYDAY":
        today_done = is_day_complete(habit, logs_by_date.get(today.strftime("%Y-%m-%d")))
        streak = 0
        check_date = today if today_done else today - timedelta(days=1)
        while True:
            done = is_day_complete(habit, logs_by_date.get(check_date.strftime("%Y-%m-%d")))
            if done:
                streak += 1
                check_date -= timedelta(days=1)
            else:
                break
        return streak

    elif repeat_type == "SPECIFIC_DAYS":
        raw_days = str(habit.get("repeat_days") or "1,2,3,4,5,6,7")
        repeat_days = [int(x.strip()) for x in raw_days.split(",") if x.strip().isdigit()]
        is_today_scheduled = (today.weekday() + 1) in repeat_days
        today_done = is_day_complete(habit, logs_by_date.get(today.strftime("%Y-%m-%d")))

        if is_today_scheduled and today_done:
            check_date = today
        else:
            prev = today - timedelta(days=1)
            safety = 0
            while (prev.weekday() + 1) not in repeat_days and safety < 14:
                prev -= timedelta(days=1)
                safety += 1
            check_date = prev

        streak = 0
        safety = 0
        while safety < 365:
            safety += 1
            if (check_date.weekday() + 1) in repeat_days:
                done = is_day_complete(habit, logs_by_date.get(check_date.strftime("%Y-%m-%d")))
                if done:
                    streak += 1
                    check_date -= timedelta(days=1)
                else:
                    break
            else:
                # Rest day -> skip without breaking streak!
                check_date -= timedelta(days=1)
        return streak

    elif repeat_type == "WEEKLY_X":
        weekly_target = max(1, min(7, habit.get("weekly_target_count") or 3))
        start_of_current_week = today - timedelta(days=today.weekday())
        current_week_done = 0
        d = start_of_current_week
        while d <= today:
            if is_day_complete(habit, logs_by_date.get(d.strftime("%Y-%m-%d"))):
                current_week_done += 1
            d += timedelta(days=1)

        current_week_target_reached = current_week_done >= weekly_target
        days_remaining_in_week = 7 - (today.weekday() + 1)
        current_week_possible = (current_week_done + days_remaining_in_week) >= weekly_target

        if not current_week_target_reached and not current_week_possible:
            return 0

        streak = 1 if current_week_target_reached else 0
        check_week_start = start_of_current_week - timedelta(weeks=1)
        week_safety = 0

        while week_safety < 52:
            week_safety += 1
            week_done_count = 0
            for offset in range(7):
                day_in_week = check_week_start + timedelta(days=offset)
                if is_day_complete(habit, logs_by_date.get(day_in_week.strftime("%Y-%m-%d"))):
                    week_done_count += 1
            if week_done_count >= weekly_target:
                streak += 1
                check_week_start -= timedelta(weeks=1)
            else:
                break
        return streak

    return 0

def calculate_habit_adherence(habit: dict, logs_by_date: dict, today) -> float:
    """Calculates adherence rate taking start_date into account."""
    if not logs_by_date:
        return 100.0
    start_date = get_habit_start_date(habit)
    repeat_type = (habit.get("repeat_type") or "EVERYDAY").upper()

    if repeat_type == "SPECIFIC_DAYS":
        raw_days = str(habit.get("repeat_days") or "1,2,3,4,5,6,7")
        repeat_days = [int(x.strip()) for x in raw_days.split(",") if x.strip().isdigit()]
        total_scheduled_done = 0
        total_scheduled_missed = 0
        for date_str, record in logs_by_date.items():
            try:
                d = datetime.strptime(date_str, "%Y-%m-%d").date()
            except Exception:
                continue
            if (d.weekday() + 1) in repeat_days and d >= start_date:
                if is_day_complete(habit, record):
                    total_scheduled_done += 1
                elif record.get("state") == "MISSED":
                    total_scheduled_missed += 1
        total = total_scheduled_done + total_scheduled_missed
        return (total_scheduled_done / total * 100.0) if total > 0 else 100.0
    else:
        total_done = 0
        total_missed = 0
        for date_str, record in logs_by_date.items():
            try:
                d = datetime.strptime(date_str, "%Y-%m-%d").date()
            except Exception:
                continue
            if d >= start_date:
                if is_day_complete(habit, record):
                    total_done += 1
                elif record.get("state") == "MISSED":
                    total_missed += 1
        total = total_done + total_missed
        return (total_done / total * 100.0) if total > 0 else 100.0

def format_habit_action_reply(
    habit: dict,
    status: str,
    notes: str = None,
    current_val: int = None,
    header: str = None
) -> str:
    """Formats a clean, rich Telegram card with bold labels and icons for habit actions."""
    title = habit.get("title", "")
    h_type = (habit.get("type") or "YES_NO").upper()
    if h_type == "TARGET":
        tgt_val = habit.get("target_value") or 1
        unit = habit.get("target_unit") or ""
        unit_str = f" {unit}" if unit else ""
        if current_val is not None:
            type_str = f"Target ({current_val}/{tgt_val}{unit_str})"
        else:
            type_str = f"Target ({tgt_val}{unit_str})"
    else:
        type_str = "Yes/No"

    repeat_str = format_repeat_schedule(habit).capitalize()
    creation_date = habit.get("start_date") or datetime.now(DEFAULT_TIMEZONE).strftime("%Y-%m-%d")
    notes_format = habit.get("note_prompt") or "BOTH"
    notes_display = format_display_note(notes) if notes else "None"

    lines = []
    if header:
        h_text = header.strip()
        if not h_text.startswith("*") and not h_text.startswith("**"):
            h_text = f"*{h_text}*"
        lines.append(h_text)
        lines.append("")

    lines.append(f"✨ *Habit:* {title}")
    lines.append(f"🎯 *Type:* {type_str}")
    lines.append(f"🔁 *Repeat:* {repeat_str}")
    lines.append(f"📅 *Created:* {creation_date}")
    lines.append(f"📝 *Notes Format:* {notes_format}")
    if notes_display and notes_display != "None":
        lines.append(f"💬 *Notes:* {notes_display}")
    lines.append(f"⚡ *Status:* {status}")

    return "\n".join(lines)

# ==========================================
# HABIT LLM TOOLS
# ==========================================
def add_habit(
    title: str,
    type: str,
    repeat_type: str,
    repeat_days: str = None,
    weekly_target_count: int = None,
    target_value: int = None,
    target_unit: str = None,
    reminder_time: str = None,
    reminder_enabled: bool = False,
    start_date: str = None,
    note_prompt: str = "BOTH"
) -> str:
    """
    Creates a new habit in KAIRO.
    Mandatory fields:
    - title: name of habit
    - type: 'YES_NO' or 'TARGET'
    - repeat_type: 'EVERYDAY', 'SPECIFIC_DAYS', or 'WEEKLY_X'
    (If TARGET: target_value and target_unit are required)
    (If SPECIFIC_DAYS: repeat_days is required, e.g. 'Mon, Wed, Fri' or '1,3,5')
    (If WEEKLY_X: weekly_target_count is required, e.g. 3)

    Optional fields:
    - reminder_time: e.g. '08:00 AM'
    - reminder_enabled: True or False (default False)
    - start_date: 'YYYY-MM-DD' (defaults to today)
    - note_prompt: 'BOTH', 'DONE_ONLY', 'MISSED_ONLY', 'NONE' (defaults to 'BOTH')
    """
    user_code = current_user.get("user_code", "")
    if not user_code:
        return "ERROR: No active user session."

    clean_title = title.strip()
    clean_type = type.upper().strip()
    clean_repeat = repeat_type.upper().strip()

    if clean_type not in ("YES_NO", "TARGET"):
        return f"ERROR: Invalid habit type '{type}'. Must be 'YES_NO' or 'TARGET'."
    if clean_repeat not in ("EVERYDAY", "SPECIFIC_DAYS", "WEEKLY_X"):
        return f"ERROR: Invalid repeat_type '{repeat_type}'. Must be 'EVERYDAY', 'SPECIFIC_DAYS', or 'WEEKLY_X'."

    if clean_type == "TARGET":
        if target_value is None or target_value < 1:
            return "ERROR: For a TARGET habit, target_value (integer >= 1) is mandatory."
        if not target_unit or not str(target_unit).strip():
            return "ERROR: For a TARGET habit, target_unit (e.g. glasses, lt, pages, mins) is mandatory."

    if clean_repeat == "SPECIFIC_DAYS" and not repeat_days:
        return "ERROR: For SPECIFIC_DAYS repeat, repeat_days (e.g. 'Mon, Wed, Fri' or '1,3,5') is mandatory."

    if clean_repeat == "WEEKLY_X" and (weekly_target_count is None or weekly_target_count < 1):
        return "ERROR: For WEEKLY_X repeat, weekly_target_count (1..7) is mandatory."

    parsed_days = parse_repeat_days(repeat_days) if clean_repeat == "SPECIFIC_DAYS" else "1,2,3,4,5,6,7"
    w_count = weekly_target_count if clean_repeat == "WEEKLY_X" else 3
    t_val = target_value if clean_type == "TARGET" else 1
    t_unit = str(target_unit).strip() if clean_type == "TARGET" and target_unit else ""

    today_iso = datetime.now(DEFAULT_TIMEZONE).strftime("%Y-%m-%d")
    s_date = start_date.strip() if start_date and start_date.strip() else today_iso

    clean_note_prompt = note_prompt.upper().strip() if note_prompt else "BOTH"
    if clean_note_prompt not in ("BOTH", "DONE_ONLY", "MISSED_ONLY", "NONE"):
        clean_note_prompt = "BOTH"

    r_enabled = bool(reminder_enabled or reminder_time)
    r_time = reminder_time.strip() if reminder_time and reminder_time.strip() else None

    existing = fetch_all_habits_for_user()
    next_pos = max([h.get("position", 0) for h in existing], default=-1) + 1

    habit_id = str(uuid.uuid4())
    now_ms = int(time.time() * 1000)

    payload = {
        "id": habit_id,
        "user_code": user_code,
        "title": clean_title,
        "type": clean_type,
        "target_value": t_val,
        "target_unit": t_unit,
        "repeat_type": clean_repeat,
        "repeat_days": parsed_days,
        "weekly_target_count": w_count,
        "reminder_enabled": r_enabled,
        "reminder_time": r_time,
        "reminder_tone_uri": None,
        "reminder_tone_title": None,
        "position": next_pos,
        "note_prompt": clean_note_prompt,
        "start_date": s_date,
        "created_at": now_ms,
        "updated_at": now_ms
    }

    url = f"{SUPABASE_URL}/rest/v1/habits"
    headers = supabase_headers()
    headers["Prefer"] = "return=representation"
    res = requests.post(url, headers=headers, json=payload, timeout=10)

    if res.status_code not in (200, 201):
        return f"ERROR: Failed to save habit to database ({res.status_code}): {res.text}"

    saved_habit = payload
    dispatch_alarm_wakeup()

    today = datetime.now(DEFAULT_TIMEZONE).date()
    if is_rest_day(saved_habit, today):
        today_status = "Rest Day"
    else:
        today_status = "Not Done"

    return format_habit_action_reply(
        saved_habit,
        status=today_status,
        notes=None,
        current_val=0,
        header="✅ Successfully added habit!"
    )

def edit_habit(
    habit_query: str,
    new_title: str = None,
    new_type: str = None,
    new_target_value: int = None,
    new_target_unit: str = None,
    new_repeat_type: str = None,
    new_repeat_days: str = None,
    new_weekly_target_count: int = None,
    new_reminder_time: str = None,
    new_reminder_enabled: bool = None,
    new_note_prompt: str = None,
    new_start_date: str = None
) -> str:
    """
    Edits an existing habit. No mandatory fields — only the fields provided will be updated, keeping everything else unchanged.
    - habit_query: name or approximate search query for the habit to edit
    - new_title: new title (optional)
    - new_type: 'YES_NO' or 'TARGET' (optional)
    - new_target_value: new integer target value (optional)
    - new_target_unit: new target unit like 'glasses', 'lt', 'pages' (optional)
    - new_repeat_type: 'EVERYDAY', 'SPECIFIC_DAYS', or 'WEEKLY_X' (optional)
    - new_repeat_days: e.g. 'Mon, Wed, Fri' or '1,3,5' (optional)
    - new_weekly_target_count: integer 1..7 (optional)
    - new_reminder_time: e.g. '08:00 AM' (optional)
    - new_reminder_enabled: bool (optional)
    - new_note_prompt: 'BOTH', 'DONE_ONLY', 'MISSED_ONLY', 'NONE' (optional)
    - new_start_date: 'YYYY-MM-DD' (optional)
    """
    user_code = current_user.get("user_code", "")
    if not user_code:
        return "ERROR: No active user session."

    habit, candidates = resolve_habit_by_query(habit_query)
    if not habit:
        cand_str = "\n".join(f"- {c.get('title')}" for c in candidates) if candidates else "No habits found."
        return f"Could not find habit matching '{habit_query}'. Available habits:\n{cand_str}"

    habit_id = habit["id"]
    updates = {}
    changes_desc = []

    if new_title and new_title.strip():
        updates["title"] = new_title.strip()
        changes_desc.append(f"Title: {updates['title']}")

    if new_type and new_type.upper().strip() in ("YES_NO", "TARGET"):
        updates["type"] = new_type.upper().strip()
        changes_desc.append(f"Type: {updates['type']}")

    if new_target_value is not None and new_target_value >= 1:
        updates["target_value"] = new_target_value
        changes_desc.append(f"Target: {new_target_value}")

    if new_target_unit is not None:
        updates["target_unit"] = new_target_unit.strip()
        changes_desc.append(f"Unit: {updates['target_unit']}")

    if new_repeat_type and new_repeat_type.upper().strip() in ("EVERYDAY", "SPECIFIC_DAYS", "WEEKLY_X"):
        updates["repeat_type"] = new_repeat_type.upper().strip()
        changes_desc.append(f"Repeat: {updates['repeat_type']}")

    if new_repeat_days is not None:
        updates["repeat_days"] = parse_repeat_days(new_repeat_days)
        changes_desc.append(f"Days: {updates['repeat_days']}")

    if new_weekly_target_count is not None and new_weekly_target_count >= 1:
        updates["weekly_target_count"] = new_weekly_target_count
        changes_desc.append(f"Weekly Target: {new_weekly_target_count}")

    if new_reminder_time is not None:
        updates["reminder_time"] = new_reminder_time.strip() if new_reminder_time.strip() else None
        if updates["reminder_time"]:
            updates["reminder_enabled"] = True
        changes_desc.append(f"Reminder: {updates['reminder_time']}")

    if new_reminder_enabled is not None:
        updates["reminder_enabled"] = bool(new_reminder_enabled)
        changes_desc.append(f"Reminder Enabled: {updates['reminder_enabled']}")

    if new_note_prompt and new_note_prompt.upper().strip() in ("BOTH", "DONE_ONLY", "MISSED_ONLY", "NONE"):
        updates["note_prompt"] = new_note_prompt.upper().strip()
        changes_desc.append(f"Note Prompt: {updates['note_prompt']}")

    if new_start_date and new_start_date.strip():
        updates["start_date"] = new_start_date.strip()
        changes_desc.append(f"Start Date: {updates['start_date']}")

    if not updates:
        return f"No valid changes provided for habit '{habit.get('title')}'. Habit remains unchanged."

    updates["updated_at"] = int(time.time() * 1000)

    url = f"{SUPABASE_URL}/rest/v1/habits?id=eq.{habit_id}"
    res = requests.patch(url, headers=supabase_headers(), json=updates, timeout=10)
    if res.status_code not in (200, 204):
        return f"ERROR: Failed to update habit in database ({res.status_code}): {res.text}"

    updated_habit = dict(habit)
    updated_habit.update(updates)
    dispatch_alarm_wakeup()

    today = datetime.now(DEFAULT_TIMEZONE).date()
    today_iso = today.strftime("%Y-%m-%d")
    logs = fetch_all_habit_logs_for_user(habit_id=habit_id)
    today_rec = next((l for l in logs if l.get("date") == today_iso), None)

    if is_rest_day(updated_habit, today):
        today_status = "Rest Day"
    elif is_day_complete(updated_habit, today_rec):
        today_status = "Done"
    elif today_rec and today_rec.get("state") == "MISSED":
        today_status = "Not Done"
    elif today_rec and (today_rec.get("value") or 0) > 0:
        h_type = (updated_habit.get("type") or "YES_NO").upper()
        if h_type == "TARGET":
            today_status = f"{today_rec.get('value')}/{updated_habit.get('target_value', 1)} {updated_habit.get('target_unit', '')} (In Progress)"
        else:
            today_status = "In Progress"
    else:
        today_status = "Not Done"

    curr_val = today_rec.get("value") if today_rec else 0
    curr_note = today_rec.get("note") if today_rec else None

    changes_info = f" ({', '.join(changes_desc)})" if changes_desc else ""
    header_text = f"✏️ Successfully updated habit!{changes_info}"

    return format_habit_action_reply(
        updated_habit,
        status=today_status,
        notes=curr_note,
        current_val=curr_val,
        header=header_text
    )

def delete_habit(habit_query: str) -> str:
    """
    Deletes a habit and all its historical logs from KAIRO.
    - habit_query: name or approximate search query for the habit to delete
    """
    user_code = current_user.get("user_code", "")
    if not user_code:
        return "ERROR: No active user session."

    habit, candidates = resolve_habit_by_query(habit_query)
    if not habit:
        cand_str = "\n".join(f"- {c.get('title')}" for c in candidates) if candidates else "No habits found."
        return f"Could not find habit matching '{habit_query}'. Available habits:\n{cand_str}"

    habit_id = habit["id"]
    habit_title = habit.get("title", "")

    # 1. Delete logs
    url_logs = f"{SUPABASE_URL}/rest/v1/habit_logs?habit_id=eq.{habit_id}"
    requests.delete(url_logs, headers=supabase_headers(), timeout=10)

    # 2. Delete habit
    url_habit = f"{SUPABASE_URL}/rest/v1/habits?id=eq.{habit_id}"
    res = requests.delete(url_habit, headers=supabase_headers(), timeout=10)

    if res.status_code not in (200, 204):
        return f"ERROR: Failed to delete habit ({res.status_code}): {res.text}"

    dispatch_alarm_wakeup()
    return f"Habit '{habit_title}' and all its logs have been deleted successfully."

def mark_habit(
    habit_query: str,
    action: str,
    date_str: str = "today",
    delta: int = None,
    target_value: int = None,
    note: str = None
) -> str:
    """
    Marks, unmarks, increments, decrements, or clears a habit.
    - habit_query: name or approximate search query for the habit
    - action: 'done' | 'not_done' | 'empty' | 'increment' | 'decrement' | 'set_value' | 'toggle'
    - date_str: 'today', 'yesterday', or specific date like '2026-10-14' (default 'today')
    - delta: optional step for increment/decrement (default 1)
    - target_value: optional value for 'set_value'
    - note: optional note text. ONLY provided if the user explicitly provided/mentioned a note!
    """
    user_code = current_user.get("user_code", "")
    if not user_code:
        return "ERROR: No active user session."

    habit, candidates = resolve_habit_by_query(habit_query)
    if not habit:
        cand_str = "\n".join(f"- {c.get('title')}" for c in candidates) if candidates else "No habits found."
        return f"Could not find habit matching '{habit_query}'. Available habits:\n{cand_str}"

    target_date, date_iso, day_name = resolve_habit_date(date_str)
    today = datetime.now(DEFAULT_TIMEZONE).date()

    if target_date > today:
        return f"Cannot mark or modify habits for future dates ({date_iso})."

    # Rest Day Check for SPECIFIC_DAYS (Cases 3 & 4)
    if is_rest_day(habit, target_date):
        habit_title = habit.get("title", "")
        return f"Today / changes for requested day ({date_iso}, {day_name}) is a rest day for '{habit_title}'. Nothing can be modified or added."

    habit_id = habit["id"]
    h_type = (habit.get("type") or "YES_NO").upper()
    h_repeat = (habit.get("repeat_type") or "EVERYDAY").upper()
    habit_tgt_val = habit.get("target_value") or 1

    # Fetch existing logs for this habit
    existing_logs = fetch_all_habit_logs_for_user(habit_id=habit_id)
    logs_by_date = {log["date"]: log for log in existing_logs}
    current_record = logs_by_date.get(date_iso) or {}
    current_val = current_record.get("value") or 0
    current_state = current_record.get("state") or "UNMARKED"
    current_note = current_record.get("note")

    action_clean = action.lower().strip()

    # Support cycle / toggle
    if action_clean in ("toggle", "cycle"):
        if current_state == "DONE":
            action_clean = "not_done"
        elif current_state == "MISSED":
            action_clean = "empty" if target_date == today else "done"
        else:
            action_clean = "done"

    # Rule: Empty / Clear is ONLY allowed for TODAY!
    if action_clean in ("empty", "clear", "unmark", "reset"):
        if target_date < today:
            return f"Cannot clear or set to empty for past days ({date_iso}). Past days can only be marked as Done or Missed (Not Done)."
        
        # Delete from Supabase
        url = f"{SUPABASE_URL}/rest/v1/habit_logs?habit_id=eq.{habit_id}&date=eq.{date_iso}"
        requests.delete(url, headers=supabase_headers(), timeout=10)
        dispatch_alarm_wakeup()
        return format_habit_action_reply(habit, status="Empty", notes=None, current_val=0, header="⚪ Habit Log Cleared")

    # Note handling: only if explicitly provided by user
    clean_user_note = note.strip() if note and note.strip() else None

    if h_type == "YES_NO":
        if action_clean in ("done", "complete", "yes", "mark"):
            new_state = "DONE"
            new_val = 1
            if clean_user_note:
                final_note = clean_user_note
            elif current_note and current_note.strip().lower() != "missed":
                final_note = current_note
            else:
                final_note = None

            logs_by_date[date_iso] = {"state": new_state, "value": new_val}
            weekly_count = count_weekly_completions(habit, logs_by_date, target_date)
            if h_repeat == "WEEKLY_X":
                w_tgt = habit.get("weekly_target_count") or 3
                if weekly_count > w_tgt:
                    status_text = f"Done (Bonus! Weekly target reached: {weekly_count}/{w_tgt})"
                elif weekly_count == w_tgt:
                    status_text = f"Done (Weekly target completed: {weekly_count}/{w_tgt})"
                else:
                    status_text = f"Done ({weekly_count}/{w_tgt} this week)"
            else:
                status_text = "Done"

        elif action_clean in ("not_done", "not done", "missed", "no", "uncomplete", "x"):
            new_state = "MISSED"
            new_val = 0
            final_note = clean_user_note or "missed"
            status_text = "Not Done"
        else:
            return f"Unrecognized action '{action}' for yes/no habit. Supported: done, not_done, empty, toggle."

    else: # TARGET
        if action_clean in ("done", "complete", "mark_all", "all"):
            new_val = habit_tgt_val
            new_state = "DONE"
            if clean_user_note:
                inc_map = parse_increment_notes(current_note)
                inc_map[new_val] = clean_user_note
                final_note = serialize_increment_notes(inc_map)
            elif current_note and current_note.strip().lower() != "missed":
                final_note = current_note
            else:
                final_note = None

            logs_by_date[date_iso] = {"state": new_state, "value": new_val}
            weekly_count = count_weekly_completions(habit, logs_by_date, target_date)
            if h_repeat == "WEEKLY_X":
                w_tgt = habit.get("weekly_target_count") or 3
                if weekly_count > w_tgt:
                    status_text = f"Done (Bonus! Weekly target reached: {weekly_count}/{w_tgt})"
                elif weekly_count == w_tgt:
                    status_text = f"Done (Weekly target completed: {weekly_count}/{w_tgt})"
                else:
                    status_text = f"Done ({weekly_count}/{w_tgt} this week)"
            else:
                status_text = "Done"

        elif action_clean in ("not_done", "not done", "missed", "x", "0"):
            new_val = 0
            new_state = "MISSED"
            final_note = clean_user_note or "missed"
            status_text = "Not Done"

        elif action_clean in ("increment", "+1", "inc", "add"):
            step = delta if delta is not None else 1
            new_val = min(habit_tgt_val, max(0, current_val + step))
            new_state = "DONE" if new_val >= habit_tgt_val else ("MISSED" if target_date < today else "UNMARKED")
            
            inc_map = parse_increment_notes(current_note)
            if clean_user_note:
                inc_map[new_val] = clean_user_note
            final_note = serialize_increment_notes(inc_map) if inc_map else None

            logs_by_date[date_iso] = {"state": new_state, "value": new_val}
            if new_state == "DONE" and h_repeat == "WEEKLY_X":
                weekly_count = count_weekly_completions(habit, logs_by_date, target_date)
                w_tgt = habit.get("weekly_target_count") or 3
                if weekly_count > w_tgt:
                    status_text = f"+{step} (Bonus! Weekly target reached: {weekly_count}/{w_tgt})"
                elif weekly_count == w_tgt:
                    status_text = f"+{step} (Weekly target completed: {weekly_count}/{w_tgt})"
                else:
                    status_text = f"+{step}"
            else:
                status_text = f"+{step}" if step > 0 else f"{step}"

        elif action_clean in ("decrement", "-1", "dec", "subtract"):
            step = abs(delta) if delta is not None else 1
            new_val = max(0, current_val - step)
            new_state = "DONE" if new_val >= habit_tgt_val else ("MISSED" if target_date < today else "UNMARKED")

            inc_map = parse_increment_notes(current_note)
            if current_val in inc_map:
                del inc_map[current_val]
            inc_map = {k: v for k, v in inc_map.items() if k <= new_val}
            final_note = serialize_increment_notes(inc_map) if inc_map else None
            status_text = f"-{step}"

        elif action_clean in ("set_value", "set"):
            if target_value is None:
                return "ERROR: target_value must be provided for set_value action."
            new_val = min(habit_tgt_val, max(0, target_value))
            new_state = "DONE" if new_val >= habit_tgt_val else ("MISSED" if target_date < today else "UNMARKED")
            
            inc_map = parse_increment_notes(current_note)
            if clean_user_note:
                inc_map[new_val] = clean_user_note
            inc_map = {k: v for k, v in inc_map.items() if k <= new_val}
            final_note = serialize_increment_notes(inc_map) if inc_map else None
            status_text = f"Set to {new_val}"

        else:
            return f"Unrecognized action '{action}' for target habit. Supported: increment, decrement, done, not_done, empty, set_value."

    if new_val == 0 and new_state == "UNMARKED" and not final_note:
        url = f"{SUPABASE_URL}/rest/v1/habit_logs?habit_id=eq.{habit_id}&date=eq.{date_iso}"
        requests.delete(url, headers=supabase_headers(), timeout=10)
    else:
        log_id = f"{habit_id}_{date_iso}"
        payload = {
            "id": log_id,
            "habit_id": habit_id,
            "user_code": user_code,
            "date": date_iso,
            "state": new_state,
            "value": new_val,
            "note": final_note,
            "created_at": int(time.time() * 1000)
        }
        url = f"{SUPABASE_URL}/rest/v1/habit_logs"
        headers = supabase_headers()
        headers["Prefer"] = "resolution=merge-duplicates,return=minimal"
        requests.post(url, headers=headers, json=payload, timeout=10)

    dispatch_alarm_wakeup()
    action_header = "✅ Habit Marked Done!" if new_state == "DONE" else ("❌ Habit Marked Not Done" if new_state == "MISSED" else "📈 Progress Logged!")
    return format_habit_action_reply(habit, status=status_text, notes=final_note, current_val=new_val, header=action_header)

def get_habit_analytics(habit_query: str = None) -> str:
    """
    Retrieves habit analytics, streaks, and discipline statistics.
    - habit_query: optional habit name. If specified, returns in-depth analytics for that specific habit. If omitted or 'all', returns the overall discipline dashboard.
    """
    user_code = current_user.get("user_code", "")
    if not user_code:
        return "ERROR: No active user session."

    habits = fetch_all_habits_for_user()
    if not habits:
        return "You have no habits created yet. Use 'add habit' to get started!"

    all_logs = fetch_all_habit_logs_for_user()
    logs_by_habit = {}
    for log in all_logs:
        h_id = log["habit_id"]
        if h_id not in logs_by_habit:
            logs_by_habit[h_id] = {}
        logs_by_habit[h_id][log["date"]] = log

    today = datetime.now(DEFAULT_TIMEZONE).date()
    today_iso = today.strftime("%Y-%m-%d")

    # If specific habit requested
    if habit_query and habit_query.strip().lower() not in ("all", "overall", "summary", "overview"):
        habit, candidates = resolve_habit_by_query(habit_query, habits)
        if not habit:
            cand_str = "\n".join(f"- {c.get('title')}" for c in candidates) if candidates else "No habits found."
            return f"Could not find habit matching '{habit_query}'. Available habits:\n{cand_str}"

        habit_id = habit["id"]
        habit_logs = logs_by_habit.get(habit_id, {})
        streak = calculate_habit_streak(habit, habit_logs, today)
        adherence = calculate_habit_adherence(habit, habit_logs, today)
        start_date = get_habit_start_date(habit)

        done_count = sum(1 for d, rec in habit_logs.items() if is_day_complete(habit, rec) and d >= start_date.strftime("%Y-%m-%d"))
        missed_count = sum(1 for d, rec in habit_logs.items() if rec.get("state") == "MISSED" and d >= start_date.strftime("%Y-%m-%d"))
        best_streak = max(streak, int(done_count / 2))

        is_rest = is_rest_day(habit, today)
        today_rec = habit_logs.get(today_iso)
        if is_rest:
            today_status = "🏖️ Rest Day"
        elif is_day_complete(habit, today_rec):
            today_status = "✅ Completed"
        elif today_rec and today_rec.get("state") == "MISSED":
            today_status = "❌ Missed"
        elif today_rec and (today_rec.get("value") or 0) > 0:
            today_status = f"🔄 In Progress ({today_rec.get('value')}/{habit.get('target_value', 1)} {habit.get('target_unit', '')})"
        else:
            today_status = "⚪ Not Done Yet"

        h_repeat = (habit.get("repeat_type") or "EVERYDAY").upper()
        weekly_str = ""
        if h_repeat == "WEEKLY_X":
            w_done = count_weekly_completions(habit, habit_logs, today)
            w_tgt = habit.get("weekly_target_count") or 3
            weekly_str = f"\n📊 **This Week's Progress:** {w_done}/{w_tgt} completed"
            if w_done >= w_tgt:
                weekly_str += " 🎯 Target Reached!"

        recent_notes = []
        for d in sorted(habit_logs.keys(), reverse=True):
            rec = habit_logs[d]
            note_raw = rec.get("note")
            if note_raw and note_raw != "missed":
                disp = format_display_note(note_raw)
                if disp and disp != "None":
                    recent_notes.append(f"- {d}: {disp}")
            if len(recent_notes) >= 5:
                break
        notes_str = "\n".join(recent_notes) if recent_notes else "None recorded."

        return (
            f"📊 **Habit Analytics: {habit.get('title')}**\n"
            f"━━━━━━━━━━━━━━━━━━━\n"
            f"📌 **Type:** {habit.get('type')} ({habit.get('target_value', 1)} {habit.get('target_unit', '')})\n"
            f"🔁 **Schedule:** {format_repeat_schedule(habit)}\n"
            f"📅 **Start Date:** {start_date.strftime('%b %d, %Y')}\n"
            f"🔥 **Current Streak:** {streak} day(s)\n"
            f"🏆 **Best Streak:** {best_streak} day(s)\n"
            f"📈 **Adherence Rate:** {adherence:.1f}%\n"
            f"✅ **Total Completed:** {done_count} time(s)\n"
            f"❌ **Total Missed:** {missed_count} time(s)\n"
            f"📍 **Today's Status:** {today_status}{weekly_str}\n\n"
            f"📝 **Recent Notes:**\n{notes_str}"
        )

    # Overview Analytics for all habits
    scheduled_today = []
    completed_today = 0
    total_progress_points = 0.0
    habit_lines = []

    for h in habits:
        h_id = h["id"]
        h_logs = logs_by_habit.get(h_id, {})
        streak = calculate_habit_streak(h, h_logs, today)
        is_rest = is_rest_day(h, today)
        today_rec = h_logs.get(today_iso)
        is_complete = is_day_complete(h, today_rec)

        h_type = (h.get("type") or "YES_NO").upper()
        t_val = h.get("target_value") or 1
        t_unit = h.get("target_unit") or ""
        curr_val = today_rec.get("value") or 0 if today_rec else 0

        if not is_rest:
            scheduled_today.append(h)
            if is_complete:
                completed_today += 1
                total_progress_points += 1.0
            elif h_type == "TARGET":
                total_progress_points += min(1.0, curr_val / max(1, t_val))

        if is_rest:
            icon = "🏖️"
            stat_str = "Rest Day"
        elif is_complete:
            icon = "✅"
            stat_str = "Done"
        elif today_rec and today_rec.get("state") == "MISSED":
            icon = "❌"
            stat_str = "Missed"
        elif curr_val > 0:
            icon = "🔄"
            stat_str = f"{curr_val}/{t_val} {t_unit}"
        else:
            icon = "⚪"
            stat_str = "Pending"

        unit_label = f" ({curr_val}/{t_val} {t_unit})" if h_type == "TARGET" and not is_complete and not is_rest and curr_val > 0 else ""
        habit_lines.append(f"{icon} **{h.get('title')}**{unit_label} | {stat_str} | 🔥 {streak}d streak")

    total_today = len(scheduled_today)
    pct = int((total_progress_points / total_today * 100)) if total_today > 0 else 0
    remaining_today = max(0, total_today - completed_today)
    top_streak = max([calculate_habit_streak(h, logs_by_habit.get(h["id"], {}), today) for h in habits], default=0)

    lines_str = "\n".join(habit_lines)
    return (
        f"📊 **Discipline Overview**\n"
        f"━━━━━━━━━━━━━━━━━━━\n"
        f"🎯 **Discipline Rate:** {pct}%\n"
        f"✅ **Completed Today:** {completed_today}/{total_today}\n"
        f"⏳ **Remaining Today:** {remaining_today}\n"
        f"🔥 **Top Current Streak:** {top_streak} day(s)\n\n"
        f"📋 **Today's Habits:**\n"
        f"{lines_str}\n\n"
        f"💡 _Tip: Say 'analytics for <habit>' for detailed breakdown & notes._"
    )

def list_habits() -> str:
    """Lists all habits with today's status, progress, streaks, and schedules."""
    return get_habit_analytics(habit_query="all")

# ==========================================
# USER CUSTOM RULES MANAGEMENT & TOOLS
# ==========================================
RULES_FILE_PATH = BASE_DIR / "user_rules.json"
SUPABASE_RULES_CONFIG_ID = "00000000-0000-0000-0000-000000000001"

def _load_cloud_rules() -> dict:
    try:
        url = f"{SUPABASE_URL}/rest/v1/tasks?id=eq.{SUPABASE_RULES_CONFIG_ID}&select=notes"
        res = requests.get(url, headers=supabase_headers(), timeout=10)
        if res.status_code == 200:
            rows = res.json()
            if rows and rows[0].get("notes"):
                return json.loads(rows[0]["notes"])
    except Exception as e:
        logger.error(f"Error loading cloud rules: {e}")
    return {}

def _save_cloud_rules(data: dict):
    try:
        headers = supabase_headers()
        headers["Prefer"] = "resolution=merge-duplicates,return=minimal"
        row = {
            "id": SUPABASE_RULES_CONFIG_ID,
            "title": "__BOT_RULES_DATA__",
            "notes": json.dumps(data),
            "due_date": "2099-01-01",
            "due_time": "12:00 PM",
            "is_completed": False,
            "section": "TODAY",
            "user_code": "__BOT_INTERNAL__"
        }
        requests.post(f"{SUPABASE_URL}/rest/v1/tasks", headers=headers, json=row, timeout=10)
    except Exception as e:
        logger.error(f"Error saving cloud rules: {e}")

def load_rules() -> dict:
    cloud_data = _load_cloud_rules()
    if cloud_data:
        try:
            RULES_FILE_PATH.write_text(json.dumps(cloud_data, indent=2), encoding="utf-8")
        except Exception:
            pass
        return cloud_data
    if RULES_FILE_PATH.exists():
        try:
            content = RULES_FILE_PATH.read_text(encoding="utf-8").strip()
            if content:
                data = json.loads(content)
                if data:
                    return data
        except Exception as e:
            logger.error(f"Error loading rules file: {e}")
    return {}

def save_rules(data: dict):
    try:
        RULES_FILE_PATH.write_text(json.dumps(data, indent=2), encoding="utf-8")
    except Exception as e:
        logger.error(f"Error saving rules file: {e}")
    _save_cloud_rules(data)

_on_rules_changed_cb = None

def set_rules_changed_callback(cb):
    global _on_rules_changed_cb
    _on_rules_changed_cb = cb

def _notify_rules_changed(chat_id: int = None):
    c_id = chat_id or current_user.get("chat_id")
    if _on_rules_changed_cb and c_id:
        try:
            _on_rules_changed_cb(int(c_id))
        except Exception as e:
            logger.error(f"Error in rules changed callback: {e}")

def get_user_rules(chat_id: int = None) -> list:
    c_id = chat_id or current_user.get("chat_id")
    if not c_id:
        return []
    all_rules = load_rules()
    return all_rules.get(str(c_id), [])

def add_user_rule(rule_text: str, chat_id: int = None) -> list:
    c_id = chat_id or current_user.get("chat_id")
    if not c_id:
        return []
    all_rules = load_rules()
    key = str(c_id)
    rules_list = all_rules.get(key, [])
    cleaned = rule_text.strip()
    if cleaned and cleaned not in rules_list:
        rules_list.append(cleaned)
        all_rules[key] = rules_list
        save_rules(all_rules)
        _notify_rules_changed(c_id)
    return rules_list

def delete_user_rule(rule_query: str, chat_id: int = None) -> tuple[bool, str, list]:
    """
    Deletes a specific user custom rule by index number (e.g. '1', 'rule 2') or by matching text.
    Returns (success, deleted_rule_text, remaining_rules).
    """
    c_id = chat_id or current_user.get("chat_id")
    if not c_id:
        return False, "No active chat session.", []

    all_rules = load_rules()
    key = str(c_id)
    rules_list = all_rules.get(key, [])
    if not rules_list:
        return False, "You have no active custom rules to delete.", []

    q = rule_query.strip().lower()
    deleted_text = None

    # Check for index number (e.g. "1", "rule 1", "#2", "2")
    match_num = re.search(r'\b(\d+)\b', q)
    if match_num:
        idx = int(match_num.group(1))
        if 1 <= idx <= len(rules_list):
            deleted_text = rules_list.pop(idx - 1)

    if not deleted_text:
        best_match = None
        best_score = 0.0
        best_idx = -1
        for i, r in enumerate(rules_list):
            r_lower = r.lower()
            if q in r_lower or r_lower in q:
                score = 0.95
            else:
                score = score_match(q, r_lower)
            if score > best_score:
                best_score = score
                best_match = r
                best_idx = i

        if best_match and best_score >= 0.5:
            deleted_text = rules_list.pop(best_idx)

    if deleted_text:
        all_rules[key] = rules_list
        save_rules(all_rules)
        _notify_rules_changed(c_id)
        return True, deleted_text, rules_list
    else:
        return False, f"Could not find any rule matching '{rule_query}'.", rules_list

def clear_user_rules(chat_id: int = None) -> bool:
    c_id = chat_id or current_user.get("chat_id")
    if not c_id:
        return False
    all_rules = load_rules()
    key = str(c_id)
    if key in all_rules:
        del all_rules[key]
        save_rules(all_rules)
        _notify_rules_changed(c_id)
    return True

# LLM Tools for Rules
def delete_rule(rule_query: str) -> str:
    """
    Deletes a specific user custom rule.
    - rule_query: rule number (e.g. '1', 'rule 2') or partial text of the rule to delete.
    """
    success, text, remaining = delete_user_rule(rule_query)
    if not success:
        rules_str = "\n".join([f"{i}. _{r}_" for i, r in enumerate(remaining, 1)]) if remaining else "None"
        return f"{text}\n\nYour active rules:\n{rules_str}"

    if remaining:
        rules_str = "\n".join([f"{i}. _{r}_" for i, r in enumerate(remaining, 1)])
        return f"🗑️ Rule Deleted successfully: \"{text}\"\n\n📋 Remaining Active Rules:\n{rules_str}"
    else:
        return f"🗑️ Rule Deleted successfully: \"{text}\"\n\nYou now have no custom rules active."

def clear_all_rules() -> str:
    """Clears and deletes all custom rules for the user."""
    clear_user_rules()
    return "🗑️ All custom rules have been cleared successfully. Standard default formatting will be used."

def list_user_rules() -> str:
    """Lists all active custom rules for the user."""
    rules = get_user_rules()
    if not rules:
        return "You have no custom rules set yet. Set one anytime by saying: 'rule - <your rule>'."
    rules_str = "\n".join([f"{i}. _{r}_" for i, r in enumerate(rules, 1)])
    return f"📋 Active Custom Rules:\n\n{rules_str}\n\nTo delete a rule, say: 'delete rule <number or text>'."

# Export tool list for Gemini API
TOOLS = [
    # Task Tools
    get_recent_tones,
    add_task,
    get_task_details,
    edit_task,
    snooze_task,
    reschedule_task,
    add_subtask,
    adjust_parent,
    delete_task,
    bulk_complete_tasks,
    bulk_delete_tasks,
    clear_completed_tasks,
    reorder_subtasks,
    set_task_completion,
    get_category_tasks,
    filter_tasks,
    search_tasks,
    export_tasks_data,
    # Habit Tools
    add_habit,
    edit_habit,
    delete_habit,
    mark_habit,
    get_habit_analytics,
    list_habits,
    # Rule Tools
    delete_rule,
    clear_all_rules,
    list_user_rules
]


