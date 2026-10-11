#!/usr/bin/env python3
"""
KAIRO AI Telegram Assistant & Hardware Alarm Dispatcher
BRAIN-style Architecture:
- Native Gemini 3.5 Flash Lite Function Calling with Kairo Tools
- Multi-turn persistent chat history per user
- Fuzzy & semantic task name resolution across all actions
- Multimodal support (Text, Photos, Documents, Voice Notes)
- Mandatory field enforcement (Title, Date & Time)
- Formatted success card output
- Subtask hierarchy and parent adjustment
- Categories, filters, search, and downloadable CSV/JSON exports
- Automatic device token linking and high-priority FCM phone alarm wakeups
"""

import asyncio
import json
import os
import re
import sys
import tempfile
import time
import zipfile
from datetime import datetime
from pathlib import Path

import httpx
from dotenv import load_dotenv
from google import genai
from google.genai import types
from telegram import Update
from telegram.constants import ParseMode, ChatAction
from telegram.error import NetworkError
from telegram.ext import (
    ApplicationBuilder,
    CommandHandler,
    ContextTypes,
    MessageHandler,
    filters,
)
from telegram.request import HTTPXRequest

import firebase_admin
from firebase_admin import credentials

import kairo_tools

# ==========================================
# CONFIGURATION
# ==========================================
BASE_DIR = Path(__file__).resolve().parent
CHATS_DIR = BASE_DIR / "chats"
CHATS_DIR.mkdir(parents=True, exist_ok=True)
ATTACHMENTS_DIR = BASE_DIR / "attachments"
ATTACHMENTS_DIR.mkdir(parents=True, exist_ok=True)
SERVICE_ACCOUNT_PATH = BASE_DIR / "firebase-service-account.json"
SESSION_FILE_PATH = BASE_DIR / "kairo_bot_session.json"
RULES_FILE_PATH = BASE_DIR / "user_rules.json"

load_dotenv()

TELEGRAM_TOKEN = os.getenv("TELEGRAM_TOKEN", "")
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")
PRIMARY_MODEL = "gemini-3.5-flash-lite"
FALLBACK_MODEL = "gemini-3.8-flash"

HISTORY_KEEP = 30
GROUP_WAIT_SECONDS = 2.0
TELEGRAM_FILE_LIMIT = 49 * 1024 * 1024

# Setup Firebase Admin
try:
    if not firebase_admin._apps:
        firebase_json_env = os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON")
        if firebase_json_env:
            try:
                cred_dict = json.loads(firebase_json_env)
                cred = credentials.Certificate(cred_dict)
                firebase_admin.initialize_app(cred)
                print("Firebase Admin initialized from FIREBASE_SERVICE_ACCOUNT_JSON env var.")
            except Exception as e_env:
                print(f"Error parsing FIREBASE_SERVICE_ACCOUNT_JSON env: {e_env}")
        elif SERVICE_ACCOUNT_PATH.exists():
            cred = credentials.Certificate(str(SERVICE_ACCOUNT_PATH))
            firebase_admin.initialize_app(cred)
            print("Firebase Admin initialized from local service-account file.")
        else:
            print("Warning: No Firebase credentials found. Push wakeups will be unavailable.")
except Exception as e:
    print(f"Firebase Admin initialization error: {e}")

# Setup Gemini Client
client = genai.Client(api_key=GEMINI_API_KEY)

# ==========================================
# SYSTEM PROMPT
# ==========================================
SYSTEM_PROMPT = """You are KAIRO AI, the personal executive assistant for the KAIRO Android Task & Hardware Alarm application.
You manage the user's tasks, alarms, subtasks, categories, and schedule.
Every task you create or modify syncs immediately with the user's cloud database and dispatches high-priority hardware alarm wakeups to their Android phone via Firebase Cloud Messaging.

CRITICAL RULES & PROTOCOLS:

1. MANDATORY FIELDS BEFORE CREATING ANY TASK OR SUBTASK (STRICT):
   - For EVERY task or subtask (normal task or subtask, without exception):
     The following THREE fields are strictly MANDATORY:
     1. Task Title / Name
     2. Due Date (e.g. Oct 11)
     3. Due Time (e.g. 05:30 PM)
   - IF THE USER ASKS TO ADD A TASK OR SUBTASK WITHOUT PROVIDING BOTH THE DATE AND THE TIME:
     YOU MUST NOT CALL add_task OR add_subtask!
     YOU MUST STOP AND ASK THE USER:
     "Please provide the mandatory Date and Time to schedule this task/subtask (e.g. Oct 11 at 05:30 PM)."
   - NEVER invent or guess a default date or time!
   - NO RELATIVE DATES STORED: Dates are stored as concrete dates (e.g. 'Oct 11', 'Oct 12'). NEVER store 'Today', 'Tomorrow', or 'Yesterday' in the database!
   - Priority defaults to "LOW" unless indicated otherwise.
   - Tone defaults to "Default Tone".
   - Repeat defaults to "NONE".
   - Geofence / Location: If coordinates or radius are given, pass `latitude`, `longitude`, and `location_radius` to `add_task` or `edit_task`.

2. FORMATTED SUCCESS CARD (MANDATORY ON EVERY TASK ADDITION):
   Whenever `add_task` or `add_subtask` succeeds, you MUST output a clean, well-formatted card in your reply with ALL of these fields:
   📋 **Task:** <Task Name>
   📝 **Notes:** <Notes or None>
   🔁 **Repeat:** <Repeat Details or None>
   🎯 **Priority:** <Priority>
   ⏰ **Date & Time:** <Date at Time>
   🔔 **Tone:** <Tone Name>
   📍 **Location:** <Location or None>
   📸 **Photos:** <Photos or None>
   ⚡ **Phone Status:** <Hardware Alarm scheduled on phone>

3. FUZZY & NEARLY MATCHING TASK NAMES:
   The user cannot remember exact task names! When the user refers to a task for details, editing, deletion, completion, adding subtasks, snoozing, rescheduling, or adjusting parents, and the exact name isn't given, ALWAYS pass their approximate phrasing in `task_query` to the tool. The tools use intelligent fuzzy matching and stem scoring to find the closest task. If a tool reports multiple candidate matches, show them to the user.

4. SUBTASKS & HIERARCHY RULES (100% ANDROID MOBILE PARITY):
   - Use `add_subtask` to add a subtask under a parent task (after obtaining mandatory Title, Date, and Time).
   - Adding a subtask under a completed parent reopens the parent and all its completed ancestors.
   - Completing a task completes ALL its subtasks recursively.
   - Completing ALL subtasks of a parent automatically completes the parent.
   - Reopening (unticking) a subtask automatically reopens the parent and all its ancestors.
   - Reopening (unticking) a parent automatically reopens all its subtasks recursively.
   - TIME IS MANDATORY & NO ALL-DAY TASKS: Every single task and subtask in KAIRO requires an exact alarm time without exception. If the scheduled time has passed/crossed the current time, the task is strictly OVERDUE! If the time is upcoming today, it is in TODAY.
   - Use `adjust_parent` to change a task's parent, detach children, or move a subtask back to root (`new_parent_query="none"`).
   - Use `reorder_subtasks` to rearrange the display order of subtasks under a parent.
   - Use `get_task_details` to inspect a task, its breadcrumb hierarchy path, and child subtask progress (e.g. 2/3 completed).

5. SNOOZING & RESCHEDULING (ANDROID PARITY):
   - Use `snooze_task(task_query, minutes)` when the user asks to snooze, delay, or remind them in X minutes (default 15 mins). It sets the `due_date` to "SNOOZED", section to "SNOOZED", calculates the exact trigger millisecond, and sends an urgent hardware alarm sync to their phone.
   - Use `reschedule_task(task_query, new_due_date, new_due_time)` when the user asks to reschedule, move, or postpone a task to a different day/time. It reactivates the task if it was completed or overdue. Both new date and time are mandatory.

6. TASK COMPLETION, DELETION & BULK ACTIONS:
   - Use `set_task_completion(task_query, is_completed=True)` to complete.
   - Use `set_task_completion(task_query, is_completed=False)` to reactivate.
   - Use `delete_task(task_query, delete_subtasks=True/False)` to delete a single task.
   - Use `bulk_complete_tasks(category="overdue"|"today"|"upcoming"|"all")` or pass specific `task_queries` when the user says "complete all overdue tasks", "mark all done", etc.
   - Use `bulk_delete_tasks(category="completed"|"overdue"|"all")` or pass specific `task_queries` when the user asks to delete a batch.
   - Use `clear_completed_tasks()` when the user asks to clear or remove completed tasks.

7. CATEGORIES & FILTERS:
   - Use `get_category_tasks(category)` for "overdue", "today", "upcoming", "completed", or "all".
   - Use `filter_tasks` for complex filters (status, priority LOW/MED/HIGH/URGENT/DUELESS, date: today/tomorrow/range).
   - If the user asks for a date range without specifying start and end dates, MANDATORILY ask the user for the range before filtering!

8. DOWNLOADABLE DATA EXPORTS:
   - Use `export_tasks_data(format_type="csv"|"json", filter_status, filter_category)` when the user asks to download or export their tasks. The file will automatically be delivered to their Telegram chat!

9. SEARCHING:
   - Use `search_tasks(query)` to find tasks by keyword in title, notes, or location.

Keep replies concise, professional, helpful, and formatted in clean Markdown."""

# Config with Kairo tools
CONFIG = types.GenerateContentConfig(
    system_instruction=SYSTEM_PROMPT,
    tools=kairo_tools.TOOLS,
)

# MIME types for vision & audio
MIME_TYPES = {
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".png": "image/png",
    ".webp": "image/webp",
    ".pdf": "application/pdf",
    ".ogg": "audio/ogg",
    ".mp3": "audio/mp3",
    ".wav": "audio/wav",
}

# ==========================================
# STATE & SESSIONS
# ==========================================
state = {
    "model": PRIMARY_MODEL,
    "chats": {}, # chat_id -> chat session object
}
ask_lock = asyncio.Lock()
groups = {}
background_tasks = set()

DEFAULT_USER_CODE = os.getenv("DEFAULT_USER_CODE", "")
DEFAULT_USER_NAME = os.getenv("DEFAULT_USER_NAME", "")
DEFAULT_FCM_TOKEN = os.getenv("DEFAULT_FCM_TOKEN", "")

def load_sessions() -> dict:
    if SESSION_FILE_PATH.exists():
        try:
            return json.loads(SESSION_FILE_PATH.read_text(encoding="utf-8"))
        except Exception as e:
            print(f"Error loading session file: {e}")
    return {}

def save_sessions(data: dict):
    try:
        SESSION_FILE_PATH.write_text(json.dumps(data, indent=2), encoding="utf-8")
    except Exception as e:
        print(f"Error saving session file: {e}")

def get_user_session(chat_id: int) -> dict:
    sessions = load_sessions()
    key = str(chat_id)
    session = sessions.get(key)
    if not session:
        session = {
            "logged_in": bool(DEFAULT_USER_CODE),
            "user_code": DEFAULT_USER_CODE or None,
            "user_name": DEFAULT_USER_NAME or None,
            "fcm_token": DEFAULT_FCM_TOKEN or None
        }
        sessions[key] = session
        save_sessions(sessions)
        return session

    if DEFAULT_FCM_TOKEN and not session.get("fcm_token") and session.get("user_code") == DEFAULT_USER_CODE:
        session["fcm_token"] = DEFAULT_FCM_TOKEN
        save_sessions(sessions)

    return session

def set_user_login(chat_id: int, user_code: str, user_name: str) -> dict:
    sessions = load_sessions()
    key = str(chat_id)
    session = sessions.get(key, {})
    session["logged_in"] = True
    session["user_code"] = user_code
    session["user_name"] = user_name
    sessions[key] = session
    save_sessions(sessions)
    return session

def set_user_fcm_token(chat_id: int, token: str) -> dict:
    sessions = load_sessions()
    key = str(chat_id)
    session = sessions.get(key, {})
    session["fcm_token"] = token
    sessions[key] = session
    save_sessions(sessions)
    return session

def clear_user_session(chat_id: int):
    sessions = load_sessions()
    key = str(chat_id)
    if key in sessions:
        del sessions[key]
        save_sessions(sessions)
    hist_file = CHATS_DIR / f"{chat_id}_history.json"
    if hist_file.exists():
        hist_file.unlink()
    if chat_id in state["chats"]:
        del state["chats"][chat_id]

# ==========================================
# CHAT HISTORY PERISTENCE
# ==========================================
def history_file_for(chat_id: int) -> Path:
    return CHATS_DIR / f"{chat_id}_history.json"

def is_plain_user_turn(content):
    if content.role != "user" or not content.parts:
        return False
    has_text = any(getattr(p, "text", None) for p in content.parts)
    has_response = any(getattr(p, "function_response", None) for p in content.parts)
    return has_text and not has_response

def trim_history(history):
    items = list(history)[-HISTORY_KEEP:]
    while items and not is_plain_user_turn(items[0]):
        items.pop(0)
    return items

def load_chat_history(chat_id: int):
    f = history_file_for(chat_id)
    if not f.exists():
        return None
    try:
        raw = json.loads(f.read_text(encoding="utf-8"))
        items = [types.Content.model_validate_json(text) for text in raw]
        items = trim_history(items)
        return items or None
    except Exception as e:
        print(f"Could not load chat history for {chat_id}: {e}")
        return None

def save_chat_history(chat_id: int, chat_obj):
    try:
        full = chat_obj.get_history(curated=True)
        trimmed = trim_history(full)
        f = history_file_for(chat_id)
        f.write_text(
            json.dumps([item.model_dump_json(exclude_none=True) for item in trimmed]),
            encoding="utf-8"
        )
    except Exception as e:
        print(f"Could not save chat history for {chat_id}: {e}")

def load_rules() -> dict:
    if RULES_FILE_PATH.exists():
        try:
            return json.loads(RULES_FILE_PATH.read_text(encoding="utf-8"))
        except Exception as e:
            print(f"Error loading rules file: {e}")
    return {}

def save_rules(data: dict):
    try:
        RULES_FILE_PATH.write_text(json.dumps(data, indent=2), encoding="utf-8")
    except Exception as e:
        print(f"Error saving rules file: {e}")

def get_user_rules(chat_id: int) -> list:
    all_rules = load_rules()
    return all_rules.get(str(chat_id), [])

def add_user_rule(chat_id: int, rule_text: str) -> list:
    all_rules = load_rules()
    key = str(chat_id)
    rules_list = all_rules.get(key, [])
    cleaned = rule_text.strip()
    if cleaned and cleaned not in rules_list:
        rules_list.append(cleaned)
    all_rules[key] = rules_list
    save_rules(all_rules)
    # Clear cached chat so next turn uses updated system instructions
    if chat_id in state["chats"]:
        del state["chats"][chat_id]
    return rules_list

def clear_user_rules(chat_id: int):
    all_rules = load_rules()
    key = str(chat_id)
    if key in all_rules:
        del all_rules[key]
        save_rules(all_rules)
    if chat_id in state["chats"]:
        del state["chats"][chat_id]

def build_config_for_chat(chat_id: int) -> types.GenerateContentConfig:
    rules = get_user_rules(chat_id)
    custom_rules_section = ""
    if rules:
        bullet_rules = "\n".join([f"- {r}" for r in rules])
        custom_rules_section = (
            f"\n\n9. USER CUSTOM FORMATTING & BEHAVIOR RULES (MANDATORY OVERRIDE):\n"
            f"The user has defined the following specific rules. You MUST follow them strictly in all your answers:\n"
            f"{bullet_rules}\n"
        )
    prompt = SYSTEM_PROMPT + custom_rules_section
    return types.GenerateContentConfig(
        system_instruction=prompt,
        tools=kairo_tools.TOOLS,
    )

def get_or_create_chat(chat_id: int):
    if chat_id in state["chats"]:
        return state["chats"][chat_id]
    hist = load_chat_history(chat_id)
    config = build_config_for_chat(chat_id)
    chat_obj = client.chats.create(model=state["model"], config=config, history=hist)
    state["chats"][chat_id] = chat_obj
    return chat_obj

def reset_chat(chat_id: int, history=None):
    config = build_config_for_chat(chat_id)
    chat_obj = client.chats.create(model=state["model"], config=config, history=list(history) if history else None)
    state["chats"][chat_id] = chat_obj
    return chat_obj

# ==========================================
# RESILIENT GEMINI CALLER
# ==========================================
def is_quota_error(error):
    text = str(error)
    return "429" in text or "RESOURCE_EXHAUSTED" in text

def is_busy_error(error):
    text = str(error)
    return "503" in text or "UNAVAILABLE" in text

def ask_gemini(chat_id: int, parts: list) -> str:
    chat_obj = get_or_create_chat(chat_id)
    before = list(chat_obj.get_history(curated=True))
    last_error = None

    for attempt in range(3):
        try:
            response = chat_obj.send_message(parts)
            save_chat_history(chat_id, chat_obj)
            return response.text or "Done."
        except Exception as error:
            last_error = error
            chat_obj = reset_chat(chat_id, before)
            if is_quota_error(error):
                print(f"Gemini quota error on {state['model']}: {str(error)[:200]}")
                break
            if not is_busy_error(error):
                raise
            print(f"Gemini busy, retrying {attempt + 1} of 3: {str(error)[:100]}")
            time.sleep(2)

    # Fallback to secondary model if primary exhausted
    if FALLBACK_MODEL and state["model"] != FALLBACK_MODEL:
        print(f"Switching to fallback model {FALLBACK_MODEL}")
        state["model"] = FALLBACK_MODEL
        chat_obj = reset_chat(chat_id, before)
        response = chat_obj.send_message(parts)
        save_chat_history(chat_id, chat_obj)
        return response.text or "Done."

    raise last_error

# ==========================================
# TELEGRAM HELPERS
# ==========================================
def spawn(coroutine):
    task = asyncio.create_task(coroutine)
    background_tasks.add(task)
    task.add_done_callback(background_tasks.discard)
    return task

async def safe_reply(message, text: str):
    for attempt in range(3):
        try:
            await message.reply_text(text, parse_mode=ParseMode.MARKDOWN)
            return
        except Exception:
            try:
                # Fallback to plain text if markdown formatting error
                await message.reply_text(text)
                return
            except NetworkError:
                await asyncio.sleep(1)
            except Exception as e:
                print(f"Failed to reply to Telegram: {e}")
                return

async def safe_send_document(message, file_path: str):
    for attempt in range(3):
        try:
            with open(file_path, "rb") as f:
                await message.reply_document(document=f)
            return
        except NetworkError:
            await asyncio.sleep(2)
        except Exception as e:
            print(f"Failed to send document: {e}")
            return

def build_request():
    options = {
        "connect_timeout": 30,
        "read_timeout": 30,
        "write_timeout": 30,
        "pool_timeout": 30,
        "media_write_timeout": 120,
    }
    return HTTPXRequest(**options)

AUTH_PROMPT_MESSAGE = (
    "🔒 *Authentication Required*\n\n"
    "Welcome to *KAIRO AI Assistant*!\n"
    "Please enter your **Kairo Login Code** to continue:\n\n"
    "_(You can find your code in the Kairo app under Settings > Account & Workspace)_"
)

# ==========================================
# MESSAGE PROCESSING PIPELINE
# ==========================================
async def process_user_turn(message, user_text: str, attachment_paths: list):
    chat_id = message.chat_id
    session = get_user_session(chat_id)

    # 1. AUTHENTICATION GATE
    if not session.get("logged_in"):
        candidate_code = user_text.replace("/login", "").strip().upper()
        if not candidate_code:
            await safe_reply(message, AUTH_PROMPT_MESSAGE)
            return

        await message.chat.send_action(action=ChatAction.TYPING)
        is_valid, user_name = kairo_tools.requests.get(
            f"{kairo_tools.SUPABASE_URL}/rest/v1/access_codes?code=eq.{candidate_code}&select=code,name",
            headers=kairo_tools.supabase_headers(),
            timeout=10
        ).json(), ""

        if is_valid and len(is_valid) > 0:
            name = is_valid[0].get("name", "User")
            set_user_login(chat_id, candidate_code, name)
            welcome_msg = (
                f"🎉 *Login Successful!*\n\n"
                f"Your Telegram account is now connected to your Kairo workspace.\n\n"
                f"📱 *Next step (to enable phone alarms):*\n"
                f"1. Open Kairo on your phone -> Settings\n"
                f"2. Tap the **Copy Token** button under **FIREBASE PUSH & ALARM WAKE**\n"
                f"3. Send it to me here: `/token <paste_token>` or simply paste your token!\n\n"
                f"Now you can send tasks naturally, e.g.:\n"
                f"_\"Remind me to call John at 6:00 pm today\"_"
            )
            await safe_reply(message, welcome_msg)
            return
        else:
            await safe_reply(
                message,
                f"❌ *Invalid Login Code: `{candidate_code}`*\n\n"
                f"Please check your login code in Kairo on your phone (open Settings > Account & Workspace) and enter it again:"
            )
            return

    # 2. AUTO-TOKEN CAPTURE IF USER PASTED AN FCM TOKEN
    clean_text = user_text.strip()
    if (len(clean_text) > 80 and (" " not in clean_text) and (":" in clean_text or clean_text.startswith("e") or clean_text.startswith("f"))) or ":APA91b" in clean_text:
        set_user_fcm_token(chat_id, clean_text)
        await safe_reply(
            message,
            "✅ *FCM Device Token Linked Successfully!* 📱\n\n"
            "Your Android device is now connected for direct background alarms.\n"
            "Try sending `/test` to verify your phone wakes up!"
        )
        return

    # 3. RULE MANAGEMENT INTERCEPTION ('rule - ...', '/rule ...', 'rules', 'reset rules')
    lower_text = clean_text.lower()
    if lower_text.startswith("rule -") or lower_text.startswith("rule:") or lower_text.startswith("/rule "):
        if lower_text.startswith("rule -"):
            rule_content = clean_text[6:].strip()
        elif lower_text.startswith("rule:"):
            rule_content = clean_text[5:].strip()
        else:
            rule_content = clean_text[6:].strip()

        if rule_content:
            active_rules = add_user_rule(chat_id, rule_content)
            rules_str = "\n".join([f"• _{r}_" for r in active_rules])
            reply_text = (
                f"📝 *Rule Saved!* ✅\n\n"
                f"From now on, I will follow this rule in all my responses:\n"
                f"> \"{rule_content}\"\n\n"
                f"📋 *Your Active Rules:*\n"
                f"{rules_str}\n\n"
                f"_(Say `/rules` to view them, or `/clearrules` to reset.)_"
            )
            await safe_reply(message, reply_text)
            return

    if lower_text in ("rules", "/rules", "show rules", "my rules"):
        active_rules = get_user_rules(chat_id)
        if not active_rules:
            await safe_reply(
                message,
                "ℹ️ You have no custom rules set yet.\n\n"
                "To set a rule, just say:\n"
                "• `rule - now send me in x format`\n"
                "• `rule - always give short 1-line answers`"
            )
        else:
            rules_str = "\n".join([f"• _{r}_" for r in active_rules])
            await safe_reply(
                message,
                f"📋 *Your Active Custom Rules:*\n\n{rules_str}\n\n"
                f"To add another rule, say `rule - <instruction>`.\n"
                f"To clear all rules, send `/clearrules`."
            )
        return

    if lower_text in ("clearrules", "/clearrules", "reset rules", "clear rules"):
        clear_user_rules(chat_id)
        await safe_reply(
            message,
            "🗑️ *All custom rules have been cleared.* I will use standard default formatting now."
        )
        return

    # Set tool context for this turn
    user_code = session["user_code"]
    fcm_token = session.get("fcm_token")
    kairo_tools.set_context(user_code=user_code, fcm_token=fcm_token)
    kairo_tools.outbox.clear()

    # 3. BUILD PROMPT AND MULTIMODAL PARTS
    now_str = datetime.now(kairo_tools.DEFAULT_TIMEZONE).strftime("%Y-%m-%d %I:%M %p, %A")
    recent_tones = kairo_tools.get_recent_tones()
    fcm_status = "Connected" if fcm_token else "NOT SET (Hardware alarms disabled)"

    context_header = (
        f"[Current local time: {now_str}]\n"
        f"[User Workspace Code: {user_code}]\n"
        f"[Phone Push Status: {fcm_status}]\n"
        f"[{recent_tones}]\n"
    )

    parts = [context_header]

    # Attach images, PDFs, or audio if present
    for path_str in attachment_paths:
        path = Path(path_str)
        mime = MIME_TYPES.get(path.suffix.lower())
        if mime and path.exists() and path.stat().st_size <= 20 * 1024 * 1024:
            parts.append(types.Part.from_bytes(data=path.read_bytes(), mime_type=mime))
            parts.append(f"[User attached file: {path.name}]")

    parts.append(user_text if user_text else "Please process the attached file and extract any tasks or reminders.")

    # 4. SEND TO GEMINI RESILIENTLY
    await message.chat.send_action(action=ChatAction.TYPING)
    try:
        answer = await asyncio.to_thread(ask_gemini, chat_id, parts)
        answer = answer or "Done."
    except Exception as error:
        if is_quota_error(error):
            answer = "⚠️ Gemini free quota is temporarily exhausted. Please wait 1-2 minutes and try again."
        elif is_busy_error(error):
            answer = "⚠️ Gemini server is busy right now. Please resend your message in a moment."
        else:
            answer = f"⚠️ An error occurred: {error}"

    await safe_reply(message, answer)

    # 5. DELIVER ANY GENERATED EXPORT FILES
    for file_path in list(kairo_tools.outbox):
        if os.path.exists(file_path):
            await safe_send_document(message, file_path)
            try:
                os.remove(file_path)
            except OSError:
                pass
    kairo_tools.outbox.clear()

# ==========================================
# MEDIA GROUP DEBOUNCING
# ==========================================
async def finish_media_group(group_id: str):
    try:
        await asyncio.sleep(GROUP_WAIT_SECONDS)
    except asyncio.CancelledError:
        return
    group = groups.pop(group_id, None)
    if group:
        await process_user_turn(group["message"], group["text"], group["paths"])

async def download_file_attachment(message) -> Path:
    if message.document:
        telegram_file = await message.document.get_file()
        name = message.document.file_name or "document"
    elif message.photo:
        telegram_file = await message.photo[-1].get_file()
        name = "photo_" + datetime.now().strftime("%Y%m%d_%H%M%S") + ".jpg"
    elif message.voice:
        telegram_file = await message.voice.get_file()
        name = "voice_" + datetime.now().strftime("%Y%m%d_%H%M%S") + ".ogg"
    else:
        telegram_file = await message.audio.get_file()
        name = "audio_" + datetime.now().strftime("%Y%m%d_%H%M%S") + ".mp3"

    safe_name = re.sub(r'[^a-zA-Z0-9_.-]', '_', name)
    dest_path = ATTACHMENTS_DIR / safe_name
    await telegram_file.download_to_drive(dest_path)
    return dest_path

# ==========================================
# TELEGRAM DISPATCH HANDLERS
# ==========================================
async def handle_incoming_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
    message = update.message
    if not message:
        return

    text = message.text or message.caption or ""

    # Check for text-only message
    if not (message.document or message.photo or message.voice or message.audio):
        await process_user_turn(message, text, [])
        return

    # Media message handling
    try:
        path = await download_file_attachment(message)
    except Exception as e:
        print(f"Attachment download error: {e}")
        await safe_reply(message, "⚠️ Could not download attachment. Maximum file size is 20 MB.")
        return

    group_id = message.media_group_id
    if group_id is None:
        await process_user_turn(message, text, [str(path)])
        return

    # Debounce group of media
    group = groups.setdefault(group_id, {"message": message, "text": "", "paths": [], "timer": None})
    group["paths"].append(str(path))
    if text:
        group["text"] = text
    if group["timer"] is not None:
        group["timer"].cancel()
    group["timer"] = spawn(finish_media_group(group_id))

# ==========================================
# COMMAND HANDLERS
# ==========================================
async def cmd_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    message = update.message
    chat_id = message.chat_id
    session = get_user_session(chat_id)

    if not session.get("logged_in"):
        await safe_reply(message, AUTH_PROMPT_MESSAGE)
        return

    user_name = session.get("user_name", "User")
    fcm_token = session.get("fcm_token")
    fcm_status = "✅ Connected & Active" if fcm_token else "⚠️ Not Configured"

    help_msg = (
        f"👋 *Welcome back, {user_name}!* 🤖\n\n"
        f"KAIRO AI Assistant is active and powered by **Gemini 3.5 Flash Lite** with real-time hardware phone alarm dispatching.\n\n"
        f"⚙️ *Setup:*\n"
        f"• Phone Push: {fcm_status}\n\n"
        f"📱 *Commands:*\n"
        f"• `/tasks` — View tasks (or ask for overdue, today, upcoming, completed)\n"
        f"• `/token <token>` — Link or update your phone's FCM token\n"
        f"• `/test` — Test waking your phone right now\n"
        f"• `/rules` — View your active custom formatting rules\n"
        f"• `/clearrules` — Reset all custom rules\n"
        f"• `/export` — Download your tasks as CSV or JSON\n"
        f"• `/logout` — Disconnect this session\n\n"
        f"✨ *Custom Rules:* \n"
        f"Set rules anytime by saying `rule - <your rule>`!\n"
        f"Example: `rule - now send me in x format`\n\n"
        f"💬 *Natural Language Prompts:*\n"
        f"• _\"Remind me to submit budget report at 5:00 pm today\"_\n"
        f"• _\"Add a task: Team sync tomorrow at 10 am, repeat weekdays\"_\n"
        f"• _\"What are my urgent tasks for today?\"_\n"
        f"• _\"Mark team sync as completed\"_\n"
        f"• _\"Make groceries a subtask of weekly chores\"_\n"
        f"• _\"Download all my active tasks as CSV\"_"
    )
    await safe_reply(message, help_msg)

async def cmd_logout(update: Update, context: ContextTypes.DEFAULT_TYPE):
    chat_id = update.message.chat_id
    clear_user_session(chat_id)
    await safe_reply(update.message, "👋 *Logged out successfully!*\n\n" + AUTH_PROMPT_MESSAGE)

async def cmd_token(update: Update, context: ContextTypes.DEFAULT_TYPE):
    message = update.message
    chat_id = message.chat_id
    session = get_user_session(chat_id)

    if not session.get("logged_in"):
        await safe_reply(message, AUTH_PROMPT_MESSAGE)
        return

    if not context.args:
        current_token = session.get("fcm_token")
        if current_token:
            await safe_reply(
                message,
                f"📱 *Current Device Token:*\n`{current_token}`\n\n"
                f"Status: ✅ Active & Ready for Alarms\n\n"
                f"To change it, send: `/token <new_token>` or simply paste your token directly into chat."
            )
        else:
            await safe_reply(
                message,
                "⚠️ Please provide your FCM token:\n`/token <your_copied_token>`\n\n"
                "You can copy it with 1 tap in Kairo under **Settings > Offline Tasks & Cloud Sync > FIREBASE PUSH & ALARM WAKE**."
            )
        return

    token = context.args[0].strip()
    set_user_fcm_token(chat_id, token)
    await safe_reply(
        message,
        "✅ *FCM Token Linked!* 📱\n"
        "Your Android device is now configured for direct background alarm scheduling.\n\n"
        "Try sending `/test` to verify your phone wakes up!"
    )

async def cmd_test(update: Update, context: ContextTypes.DEFAULT_TYPE):
    message = update.message
    chat_id = message.chat_id
    session = get_user_session(chat_id)

    if not session.get("logged_in"):
        await safe_reply(message, AUTH_PROMPT_MESSAGE)
        return

    token = session.get("fcm_token")
    if not token:
        await safe_reply(
            message,
            "⚠️ Cannot send test wake ping because no FCM token is linked!\n"
            "Open Kairo -> Settings -> copy token -> paste it here."
        )
        return

    await safe_reply(message, "📡 Sending high-priority wake ping to your phone...")
    kairo_tools.set_context(user_code=session["user_code"], fcm_token=token)
    success = kairo_tools.dispatch_alarm_wakeup()
    if success:
        await safe_reply(
            message,
            "⚡ *Wake ping sent successfully!* 🚀\n"
            "Kairo on your phone has received the background wake signal and synced its database."
        )
    else:
        await safe_reply(message, "❌ Failed to dispatch wake ping. Verify that the FCM token is valid.")

async def cmd_tasks(update: Update, context: ContextTypes.DEFAULT_TYPE):
    message = update.message
    chat_id = message.chat_id
    session = get_user_session(chat_id)

    if not session.get("logged_in"):
        await safe_reply(message, AUTH_PROMPT_MESSAGE)
        return

    kairo_tools.set_context(user_code=session["user_code"], fcm_token=session.get("fcm_token"))
    tasks_text = kairo_tools.get_category_tasks("all")
    await safe_reply(message, tasks_text)

async def cmd_export(update: Update, context: ContextTypes.DEFAULT_TYPE):
    message = update.message
    chat_id = message.chat_id
    session = get_user_session(chat_id)

    if not session.get("logged_in"):
        await safe_reply(message, AUTH_PROMPT_MESSAGE)
        return

    kairo_tools.set_context(user_code=session["user_code"], fcm_token=session.get("fcm_token"))
    format_type = "csv"
    if context.args and context.args[0].lower() in ("json", "csv"):
        format_type = context.args[0].lower()

    res = kairo_tools.export_tasks_data(format_type=format_type)
    await safe_reply(message, res)

    for file_path in list(kairo_tools.outbox):
        if os.path.exists(file_path):
            await safe_send_document(message, file_path)
            try:
                os.remove(file_path)
            except OSError:
                pass
    kairo_tools.outbox.clear()

async def on_error(update: Update, context: ContextTypes.DEFAULT_TYPE):
    print(f"Telegram error encountered: {context.error}")

# ==========================================
# CLOUD HEALTH SERVER (Koyeb / Render / Fly.io)
# ==========================================
import threading
from http.server import HTTPServer, BaseHTTPRequestHandler

class HealthCheckHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header("Content-type", "application/json")
        self.end_headers()
        response = json.dumps({"status": "ok", "app": "KAIRO Telegram Assistant"}).encode("utf-8")
        self.wfile.write(response)

    def log_message(self, format, *args):
        # Silence routine health check log spam
        pass

def start_health_server():
    port = int(os.getenv("PORT", "8000"))
    try:
        server = HTTPServer(("0.0.0.0", port), HealthCheckHandler)
        print(f"Cloud health server listening on port {port} for Koyeb/Render checks...")
        server.serve_forever()
    except Exception as e:
        print(f"Health server failed to bind on port {port}: {e}")

async def cmd_rules(update: Update, context: ContextTypes.DEFAULT_TYPE):
    chat_id = update.message.chat_id
    active_rules = get_user_rules(chat_id)
    if not active_rules:
        await safe_reply(
            update.message,
            "ℹ️ You have no custom rules set yet.\n\n"
            "To set a rule, just say:\n"
            "• `rule - now send me in x format`\n"
            "• `rule - always give short 1-line answers`"
        )
    else:
        rules_str = "\n".join([f"• _{r}_" for r in active_rules])
        await safe_reply(
            update.message,
            f"📋 *Your Active Custom Rules:*\n\n{rules_str}\n\n"
            f"To add another rule, say `rule - <instruction>`.\n"
            f"To clear all rules, send `/clearrules`."
        )

async def cmd_clearrules(update: Update, context: ContextTypes.DEFAULT_TYPE):
    chat_id = update.message.chat_id
    clear_user_rules(chat_id)
    await safe_reply(
        update.message,
        "🗑️ *All custom rules have been cleared.* I will use standard default formatting now."
    )

# ==========================================
# MAIN RUNNER
# ==========================================
def main():
    print(f"Starting KAIRO AI Telegram Assistant (BRAIN Architecture)...")

    # Start health server in daemon thread for cloud hosting platforms (Koyeb, Render, etc.)
    health_thread = threading.Thread(target=start_health_server, daemon=True)
    health_thread.start()

    app = (
        ApplicationBuilder()
        .token(TELEGRAM_TOKEN)
        .request(build_request())
        .get_updates_request(build_request())
        .build()
    )

    app.add_error_handler(on_error)

    app.add_handler(CommandHandler("start", cmd_start))
    app.add_handler(CommandHandler("help", cmd_start))
    app.add_handler(CommandHandler("logout", cmd_logout))
    app.add_handler(CommandHandler("token", cmd_token))
    app.add_handler(CommandHandler("test", cmd_test))
    app.add_handler(CommandHandler("tasks", cmd_tasks))
    app.add_handler(CommandHandler("list", cmd_tasks))
    app.add_handler(CommandHandler("export", cmd_export))
    app.add_handler(CommandHandler("rules", cmd_rules))
    app.add_handler(CommandHandler("clearrules", cmd_clearrules))

    app.add_handler(
        MessageHandler(
            (filters.TEXT & ~filters.COMMAND) | filters.Document.ALL | filters.PHOTO | filters.VOICE | filters.AUDIO,
            handle_incoming_message
        )
    )

    print("KAIRO AI Assistant is running and listening for Telegram updates...")
    app.run_polling(bootstrap_retries=-1)

if __name__ == "__main__":
    main()
