export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT' | 'DUELESS';

export type TaskSection = 'SNOOZED' | 'OVERDUE' | 'TODAY' | 'UPCOMING' | 'COMPLETED';

export type RepeatType = 'NONE' | 'DAILY' | 'WEEKDAYS' | 'WEEKENDS' | 'MONTHLY_DATE';

export interface Task {
  id: string;
  title: string;
  notes?: string | null;
  priority: Priority;
  dueDate: string;
  dueDateMillis?: number | null;
  dueTime?: string | null;
  location?: string | null;
  attachmentName?: string | null;
  attachmentUri?: string | null;
  isCompleted: boolean;
  section: TaskSection;
  createdAt: number;
  updatedAt: number;
  completedAt?: number | null;
  alarmToneUri?: string | null;
  alarmToneTitle?: string | null;
  repeatType?: RepeatType | string | null;
  repeatDays?: string | null;
  repeatDates?: string | null;
  parentId?: string | null;
  position: number;
  userCode: string;
  latitude?: number | null;
  longitude?: number | null;
  radiusMeters?: number | null;
  snoozeUntilMillis?: number | null;
}

export interface TaskDto {
  id: string;
  title: string;
  notes: string | null;
  priority: string;
  due_date: string;
  due_date_millis: number | null;
  due_time: string | null;
  location: string | null;
  attachment_name: string | null;
  attachment_uri: string | null;
  is_completed: boolean;
  section: string;
  created_at: number;
  updated_at: number;
  completed_at: number | null;
  alarm_tone_uri: string | null;
  alarm_tone_title: string | null;
  repeat_type: string | null;
  repeat_days: string | null;
  repeat_dates: string | null;
  parent_id: string | null;
  position: number;
  user_code: string;
  latitude: number | null;
  longitude: number | null;
  radius_meters: number | null;
}

export function dtoToTask(dto: TaskDto, snoozeUntilMillis?: number | null): Task {
  return {
    id: dto.id,
    title: dto.title,
    notes: dto.notes,
    priority: (dto.priority as Priority) || 'LOW',
    dueDate: dto.due_date || 'Today',
    dueDateMillis: dto.due_date_millis,
    dueTime: dto.due_time,
    location: dto.location,
    attachmentName: dto.attachment_name,
    attachmentUri: dto.attachment_uri,
    isCompleted: dto.is_completed ?? false,
    section: (dto.section as TaskSection) || 'TODAY',
    createdAt: dto.created_at || Date.now(),
    updatedAt: dto.updated_at || Date.now(),
    completedAt: dto.completed_at,
    alarmToneUri: dto.alarm_tone_uri,
    alarmToneTitle: dto.alarm_tone_title,
    repeatType: dto.repeat_type || 'NONE',
    repeatDays: dto.repeat_days,
    repeatDates: dto.repeat_dates,
    parentId: dto.parent_id,
    position: dto.position ?? 0,
    userCode: dto.user_code,
    latitude: dto.latitude,
    longitude: dto.longitude,
    radiusMeters: dto.radius_meters,
    snoozeUntilMillis: snoozeUntilMillis ?? null,
  };
}

export function taskToDto(task: Task, userCode: string): TaskDto {
  return {
    id: task.id,
    title: task.title,
    notes: task.notes ?? null,
    priority: task.priority,
    due_date: task.dueDate,
    due_date_millis: task.dueDateMillis ?? null,
    due_time: task.dueTime ?? null,
    location: task.location ?? null,
    attachment_name: task.attachmentName ?? null,
    attachment_uri: task.attachmentUri ?? null,
    is_completed: task.isCompleted,
    section: task.section,
    created_at: task.createdAt,
    updated_at: task.updatedAt,
    completed_at: task.completedAt ?? null,
    alarm_tone_uri: task.alarmToneUri ?? null,
    alarm_tone_title: task.alarmToneTitle ?? null,
    repeat_type: task.repeatType ?? null,
    repeat_days: task.repeatDays ?? null,
    repeat_dates: task.repeatDates ?? null,
    parent_id: task.parentId ?? null,
    position: task.position,
    user_code: userCode,
    latitude: task.latitude ?? null,
    longitude: task.longitude ?? null,
    radius_meters: task.radiusMeters ?? 500,
  };
}
