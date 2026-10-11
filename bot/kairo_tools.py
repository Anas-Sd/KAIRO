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

# Context state per turn
current_user = {"user_code": "SDANAS", "fcm_token": None}

def set_context(user_code: str, fcm_token: str = None):
    current_user["user_code"] = user_code
    current_user["fcm_token"] = fcm_token

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
            data_payload.update({
                "id": str(task.get("id", "")),
                "title": str(task.get("title", "")),
                "dueDate": str(task.get("due_date", "Today")),
                "dueTime": str(task.get("due_time", "") or ""),
                "priority": str(task.get("priority", "LOW")),
                "section": str(task.get("section", "TODAY")),
            })
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
# DATE & TIME COMPUTATION HELPER
# ==========================================
def compute_due_millis(due_date_str: str, due_time_str: str = None) -> int:
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
            for fmt in ["%b %d", "%B %d", "%Y-%m-%d", "%d/%m/%Y", "%d %b %Y"]:
                try:
                    parsed = datetime.strptime(due_date_str.strip(), fmt)
                    target_date = target_date.replace(month=parsed.month, day=parsed.day)
                    if parsed.year > 1900:
                        target_date = target_date.replace(year=parsed.year)
                    break
                except Exception:
                    pass

    hour = 9
    minute = 0
    if due_time_str:
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

def determine_section(due_millis: int, is_completed: bool) -> str:
    if is_completed:
        return "COMPLETED"
    now_ms = int(time.time() * 1000)
    now_dt = datetime.now(DEFAULT_TIMEZONE)
    due_dt = datetime.fromtimestamp(due_millis / 1000.0, DEFAULT_TIMEZONE)

    if due_millis < (now_ms - 60000):
        # overdue if in the past
        return "OVERDUE"
    elif due_dt.date() == now_dt.date():
        return "TODAY"
    else:
        return "UPCOMING"

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
    parent_task_query: str = None
) -> str:
    """
    Creates a new task in Kairo with mandatory title, due_date and due_time.
    repeat_type must be one of: NONE, DAILY, WEEKDAYS, WEEKENDS, CUSTOM_DAYS, MONTHLY_DATE, ONCE_ON_DATE.
    priority must be one of: LOW, MEDIUM, HIGH, URGENT.
    """
    if not title or not title.strip():
        return "ERROR: Task name/title is mandatory."
    if not due_date or not due_time:
        return "ERROR: Both date and time are mandatory to schedule an alarm."

    user_code = current_user["user_code"]
    task_id = str(uuid.uuid4())
    due_millis = compute_due_millis(due_date, due_time)
    now_ms = int(time.time() * 1000)

    # Resolve parent if specified
    parent_id = None
    if parent_task_query:
        parent_task, _ = resolve_task_by_query(parent_task_query)
        if parent_task:
            parent_id = parent_task["id"]

    clean_priority = priority.strip().upper() if priority else "LOW"
    if clean_priority not in ("LOW", "MEDIUM", "HIGH", "URGENT"):
        clean_priority = "LOW"

    clean_repeat = repeat_type.strip().upper() if repeat_type else "NONE"
    section = determine_section(due_millis, False)

    new_task = {
        "id": task_id,
        "title": title.strip(),
        "notes": notes.strip() if notes else None,
        "priority": clean_priority,
        "due_date": due_date.strip(),
        "due_date_millis": due_millis,
        "due_time": due_time.strip(),
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
        "position": 0
    }

    try:
        url = f"{SUPABASE_URL}/rest/v1/tasks"
        res = requests.post(url, headers=supabase_headers(), json=new_task, timeout=10)
        if res.status_code not in (200, 201):
            return f"ERROR: Failed to save task to database ({res.status_code} {res.text})"
    except Exception as e:
        return f"ERROR: Database write failed ({e})"

    # Dispatch FCM wakeup
    wake_sent = dispatch_alarm_wakeup(new_task)
    wake_label = "Phone hardware alarm registered" if wake_sent else "Saved (token not configured)"

    return json.dumps({
        "status": "SUCCESS",
        "task_name": new_task["title"],
        "notes": new_task["notes"] or "None",
        "repeat": new_task["repeat_type"] + (f" ({repeat_days})" if repeat_days else "") + (f" ({repeat_dates})" if repeat_dates else ""),
        "priority": new_task["priority"],
        "date_time": f"{new_task['due_date']} at {new_task['due_time']}",
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
    new_notes: str = None,
    new_repeat_type: str = None,
    new_repeat_days: str = None,
    new_repeat_dates: str = None,
    new_tone: str = None,
    new_location: str = None
) -> str:
    """Modifies properties of an existing task found via fuzzy matching."""
    all_tasks = fetch_all_tasks_for_user()
    task, candidates = resolve_task_by_query(task_query, all_tasks)
    if not task:
        if candidates:
            return f"Could not find '{task_query}'. Possible matches: {', '.join(c.get('title') for c in candidates[:3])}"
        return f"No task found matching '{task_query}'."

    patch_data = {"updated_at": int(time.time() * 1000)}
    if new_title: patch_data["title"] = new_title.strip()
    if new_notes is not None: patch_data["notes"] = new_notes.strip() if new_notes else None
    if new_priority: patch_data["priority"] = new_priority.strip().upper()
    if new_due_date: patch_data["due_date"] = new_due_date.strip()
    if new_due_time: patch_data["due_time"] = new_due_time.strip()
    if new_repeat_type: patch_data["repeat_type"] = new_repeat_type.strip().upper()
    if new_repeat_days is not None: patch_data["repeat_days"] = new_repeat_days.strip() if new_repeat_days else None
    if new_repeat_dates is not None: patch_data["repeat_dates"] = new_repeat_dates.strip() if new_repeat_dates else None
    if new_tone: patch_data["alarm_tone_title"] = new_tone.strip()
    if new_location is not None: patch_data["location"] = new_location.strip() if new_location else None

    # Recompute due millis if date or time changed
    effective_date = new_due_date or task.get("due_date", "Today")
    effective_time = new_due_time or task.get("due_time")
    if new_due_date or new_due_time:
        due_ms = compute_due_millis(effective_date, effective_time)
        patch_data["due_date_millis"] = due_ms
        patch_data["section"] = determine_section(due_ms, task.get("is_completed", False))

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
    due_date: str = "Today",
    due_time: str = None,
    priority: str = "LOW",
    notes: str = None
) -> str:
    """Adds a subtask under an existing parent task (resolved via fuzzy match)."""
    all_tasks = fetch_all_tasks_for_user()
    parent, candidates = resolve_task_by_query(parent_task_query, all_tasks)
    if not parent:
        if candidates:
            return f"Could not find parent task '{parent_task_query}'. Did you mean: {', '.join(c.get('title') for c in candidates[:3])}?"
        return f"Could not find parent task '{parent_task_query}'."

    effective_date = due_date or parent.get("due_date", "Today")
    effective_time = due_time or parent.get("due_time")
    return add_task(
        title=title,
        due_date=effective_date,
        due_time=effective_time or "09:00 AM",
        priority=priority,
        notes=notes,
        parent_task_query=parent["title"]
    )

def adjust_parent(task_query: str, new_parent_query: str = None) -> str:
    """
    Changes or removes the parent for a subtask/task.
    If new_parent_query is None, 'none', or 'root', the task becomes a top-level task.
    """
    all_tasks = fetch_all_tasks_for_user()
    task, candidates = resolve_task_by_query(task_query, all_tasks)
    if not task:
        if candidates:
            return f"Could not find task '{task_query}'. Candidates: {', '.join(c.get('title') for c in candidates[:3])}"
        return f"Could not find task '{task_query}'."

    new_parent_id = None
    new_parent_title = "Root (No parent)"
    if new_parent_query and new_parent_query.strip().lower() not in ("none", "root", "remove", "null"):
        parent_task, p_candidates = resolve_task_by_query(new_parent_query, all_tasks)
        if not parent_task:
            return f"Could not find parent task '{new_parent_query}'."
        if parent_task["id"] == task["id"]:
            return "ERROR: A task cannot be its own parent."
        new_parent_id = parent_task["id"]
        new_parent_title = parent_task.get("title", "Parent")

    try:
        url = f"{SUPABASE_URL}/rest/v1/tasks?id=eq.{task['id']}"
        res = requests.patch(url, headers=supabase_headers(), json={"parent_id": new_parent_id, "updated_at": int(time.time() * 1000)}, timeout=10)
        if res.status_code in (200, 204):
            dispatch_alarm_wakeup(task)
            return f"Successfully updated parent of '{task.get('title')}' to: {new_parent_title}"
        return f"Database error updating parent: {res.text}"
    except Exception as e:
        return f"Error: {e}"

def delete_task(task_query: str) -> str:
    """Deletes a task and its direct subtasks, canceling phone alarms."""
    all_tasks = fetch_all_tasks_for_user()
    task, candidates = resolve_task_by_query(task_query, all_tasks)
    if not task:
        if candidates:
            return f"Could not find task '{task_query}'. Did you mean: {', '.join(c.get('title') for c in candidates[:3])}?"
        return f"No task found matching '{task_query}'."

    task_id = task["id"]
    try:
        url = f"{SUPABASE_URL}/rest/v1/tasks?id=eq.{task_id}"
        res = requests.delete(url, headers=supabase_headers(), timeout=10)
        if res.status_code in (200, 204):
            dispatch_alarm_wakeup()
            return f"🗑️ Deleted task '{task.get('title')}' and canceled phone alarms."
        return f"Failed to delete: {res.text}"
    except Exception as e:
        return f"Error deleting task: {e}"

def set_task_completion(task_query: str, is_completed: bool) -> str:
    """Marks a task as completed (true) or active again (false)."""
    all_tasks = fetch_all_tasks_for_user()
    task, candidates = resolve_task_by_query(task_query, all_tasks)
    if not task:
        if candidates:
            return f"Could not find task '{task_query}'. Did you mean: {', '.join(c.get('title') for c in candidates[:3])}?"
        return f"No task found matching '{task_query}'."

    task_id = task["id"]
    now_ms = int(time.time() * 1000)
    section = "COMPLETED" if is_completed else determine_section(task.get("due_date_millis") or now_ms, False)

    patch_data = {
        "is_completed": is_completed,
        "section": section,
        "completed_at": now_ms if is_completed else None,
        "updated_at": now_ms
    }

    try:
        url = f"{SUPABASE_URL}/rest/v1/tasks?id=eq.{task_id}"
        res = requests.patch(url, headers=supabase_headers(), json=patch_data, timeout=10)
        if res.status_code in (200, 204):
            dispatch_alarm_wakeup({**task, **patch_data})
            status_word = "completed 🎉" if is_completed else "active again ⚡"
            return f"Task '{task.get('title')}' marked as {status_word}."
        return f"Database error: {res.text}"
    except Exception as e:
        return f"Error: {e}"

def get_category_tasks(category: str) -> str:
    """
    Retrieves tasks by category: 'overdue', 'today', 'upcoming', 'completed', or 'all'.
    """
    cat = category.strip().lower()
    tasks = fetch_all_tasks_for_user()
    now_ms = int(time.time() * 1000)
    now_dt = datetime.now(DEFAULT_TIMEZONE)

    filtered = []
    for t in tasks:
        completed = t.get("is_completed", False)
        due_ms = t.get("due_date_millis") or 0

        if cat == "completed":
            if completed: filtered.append(t)
        elif cat == "overdue":
            if not completed and due_ms > 0 and due_ms < (now_ms - 60000):
                filtered.append(t)
        elif cat == "today":
            if not completed and due_ms > 0:
                due_dt = datetime.fromtimestamp(due_ms / 1000.0, DEFAULT_TIMEZONE)
                if due_dt.date() == now_dt.date() and due_ms >= (now_ms - 60000):
                    filtered.append(t)
            elif not completed and t.get("due_date") == "Today":
                filtered.append(t)
        elif cat == "upcoming":
            if not completed and due_ms > 0:
                due_dt = datetime.fromtimestamp(due_ms / 1000.0, DEFAULT_TIMEZONE)
                if due_dt.date() > now_dt.date():
                    filtered.append(t)
            elif not completed and t.get("due_date") in ("Tomorrow", "Upcoming"):
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
