import { TaskSection } from '../types';

export function parseTimeToHourMinute(timeStr?: string | null): { hour: number; minute: number } {
  if (!timeStr) return { hour: 9, minute: 0 };
  const clean = timeStr.trim().toUpperCase();

  // Try 12-hour: "06:30 PM", "6:30 PM", "6 PM"
  const match12 = clean.match(/^(\d{1,2})(?::(\d{2}))?\s*(AM|PM)$/);
  if (match12) {
    let hour = parseInt(match12[1], 10);
    const minute = match12[2] ? parseInt(match12[2], 10) : 0;
    const meridian = match12[3];
    if (meridian === 'PM' && hour < 12) hour += 12;
    if (meridian === 'AM' && hour === 12) hour = 0;
    return { hour, minute };
  }

  // Try 24-hour: "18:30", "18"
  const match24 = clean.match(/^(\d{1,2})(?::(\d{2}))?$/);
  if (match24) {
    const hour = parseInt(match24[1], 10);
    const minute = match24[2] ? parseInt(match24[2], 10) : 0;
    return { hour, minute };
  }

  return { hour: 9, minute: 0 };
}

export function calculateTriggerMillis(
  dueDate: string,
  dueTime?: string | null,
  dueDateMillis?: number | null
): number | null {
  if (dueDateMillis && dueDateMillis > 0) {
    return dueDateMillis;
  }

  const now = new Date();
  let targetYear = now.getFullYear();
  let targetMonth = now.getMonth();
  let targetDay = now.getDate();

  const cleanDate = (dueDate || 'Today').trim().toLowerCase();
  if (cleanDate === 'today') {
    // Keep today
  } else if (cleanDate === 'tomorrow') {
    const tmrw = new Date();
    tmrw.setDate(tmrw.getDate() + 1);
    targetYear = tmrw.getFullYear();
    targetMonth = tmrw.getMonth();
    targetDay = tmrw.getDate();
  } else {
    const parsed = new Date(dueDate);
    if (!isNaN(parsed.getTime())) {
      targetYear = parsed.getFullYear();
      targetMonth = parsed.getMonth();
      targetDay = parsed.getDate();
    }
  }

  const { hour, minute } = parseTimeToHourMinute(dueTime);
  const target = new Date(targetYear, targetMonth, targetDay, hour, minute, 0, 0);
  return target.getTime();
}

export function formatDisplayDate(dateInput: Date | number): string {
  const d = typeof dateInput === 'number' ? new Date(dateInput) : dateInput;
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

export function normalizeDateString(dateStr?: string | null): string {
  if (!dateStr || !dateStr.trim()) {
    return formatDisplayDate(new Date());
  }
  const clean = dateStr.trim();
  if (clean.toUpperCase() === 'SNOOZED') return 'SNOOZED';
  const lower = clean.toLowerCase();
  const now = new Date();
  if (lower === 'today') {
    return formatDisplayDate(now);
  }
  if (lower === 'tomorrow') {
    const tmrw = new Date();
    tmrw.setDate(tmrw.getDate() + 1);
    return formatDisplayDate(tmrw);
  }
  if (lower === 'yesterday') {
    const yest = new Date();
    yest.setDate(yest.getDate() - 1);
    return formatDisplayDate(yest);
  }
  const parsed = new Date(clean);
  if (!isNaN(parsed.getTime())) {
    return formatDisplayDate(parsed);
  }
  return clean;
}

export function determineSection(
  dueDate: string,
  dueTime?: string | null,
  dueDateMillis?: number | null,
  isCompleted: boolean = false,
  snoozeUntilMillis?: number | null
): TaskSection {
  if (isCompleted) return 'COMPLETED';

  const now = Date.now();
  if (snoozeUntilMillis && snoozeUntilMillis > now) {
    return 'SNOOZED';
  }

  const cleanDate = (dueDate || '').trim().toLowerCase();
  if (cleanDate === 'snoozed') {
    return 'SNOOZED';
  }
  if (cleanDate.includes('yesterday')) {
    return 'OVERDUE';
  }

  const todayEnd = new Date();
  todayEnd.setHours(0, 0, 0, 0);
  const todayEndMs = todayEnd.getTime() + 86400000;

  const trigger = calculateTriggerMillis(dueDate, dueTime, dueDateMillis);
  if (trigger != null && trigger > 0) {
    if (trigger < now) {
      return 'OVERDUE';
    }
    if (trigger < todayEndMs) {
      return 'TODAY';
    }
    return 'UPCOMING';
  }

  return 'OVERDUE';
}

export function formatTimeDisplay(timeStr?: string | null): string {
  if (!timeStr) return '';
  const { hour, minute } = parseTimeToHourMinute(timeStr);
  const meridian = hour >= 12 ? 'PM' : 'AM';
  const displayHour = hour % 12 === 0 ? 12 : hour % 12;
  const displayMin = minute.toString().padStart(2, '0');
  return `${displayHour}:${displayMin} ${meridian}`;
}
