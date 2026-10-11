import React, { useState } from 'react';
import {
  X,
  Calendar,
  Clock,
  Flag,
  Repeat,
  Bell,
  MapPin,
  FolderTree,
  Sparkles,
} from 'lucide-react';
import { Task, Priority, RepeatType } from '../types';
import { calculateTriggerMillis, determineSection, normalizeDateString } from '../utils/dateUtils';

interface CreateEditTaskModalProps {
  initialTask?: Task | null;
  parentTaskId?: string | null;
  allTasks: Task[];
  userCode: string;
  onSave: (task: Task) => Promise<void>;
  onClose: () => void;
}

const TONES = [
  'Default (ringtone_008)',
  'Cosmic Bells',
  'Synth Horizon',
  'Deep Resonance',
  'Soft Alert',
  'Electronic Chime',
  'NONE',
];

export const CreateEditTaskModal: React.FC<CreateEditTaskModalProps> = ({
  initialTask,
  parentTaskId,
  allTasks,
  userCode,
  onSave,
  onClose,
}) => {
  const isEditing = Boolean(initialTask);

  const [title, setTitle] = useState(initialTask?.title || '');
  const [notes, setNotes] = useState(initialTask?.notes || '');
  const [priority, setPriority] = useState<Priority>(initialTask?.priority || 'LOW');

  // Due Date
  const [dueDate, setDueDate] = useState(initialTask?.dueDate || 'Today');
  const [customDateVal, setCustomDateVal] = useState(
    initialTask?.dueDate && initialTask.dueDate !== 'Today' && initialTask.dueDate !== 'Tomorrow'
      ? initialTask.dueDate
      : new Date().toISOString().split('T')[0]
  );
  const [isCustomDate, setIsCustomDate] = useState(
    initialTask?.dueDate ? initialTask.dueDate !== 'Today' && initialTask.dueDate !== 'Tomorrow' : false
  );

  // Due Time
  const [dueTime, setDueTime] = useState(initialTask?.dueTime || '09:00 AM');

  // Repeat
  const [repeatType, setRepeatType] = useState<RepeatType>(
    (initialTask?.repeatType as RepeatType) || 'NONE'
  );
  const [repeatDates, setRepeatDates] = useState(initialTask?.repeatDates || '');

  // Tone
  const [tone, setTone] = useState(initialTask?.alarmToneTitle || 'Default (ringtone_008)');

  // Location
  const [location, setLocation] = useState(initialTask?.location || '');

  // Parent Task
  const [parentId, setParentId] = useState<string | null>(
    initialTask?.parentId ?? parentTaskId ?? null
  );

  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) {
      setError('Please enter a task title (mandatory).');
      return;
    }
    if (!dueTime || !dueTime.trim()) {
      setError('Due time is mandatory for alarms. Please specify a time.');
      return;
    }

    setSaving(true);
    setError(null);

    try {
      const rawDueDate = isCustomDate ? customDateVal : dueDate;
      const finalDueDate = normalizeDateString(rawDueDate);
      const dueMillis = calculateTriggerMillis(finalDueDate, dueTime);
      const section = determineSection(finalDueDate, dueTime, dueMillis, false);

      const taskToSave: Task = {
        id: initialTask?.id || crypto.randomUUID(),
        title: title.trim(),
        notes: notes.trim() ? notes.trim() : null,
        priority,
        dueDate: finalDueDate,
        dueTime: dueTime.trim() ? dueTime.trim() : null,
        dueDateMillis: dueMillis,
        location: location.trim() ? location.trim() : null,
        attachmentName: initialTask?.attachmentName || null,
        attachmentUri: initialTask?.attachmentUri || null,
        isCompleted: false, // Creating or editing always reactivates
        section,
        createdAt: initialTask?.createdAt || Date.now(),
        updatedAt: Date.now(),
        completedAt: null,
        alarmToneUri: initialTask?.alarmToneUri || null,
        alarmToneTitle: tone,
        repeatType,
        repeatDays:
          repeatType === 'WEEKDAYS'
            ? 'MON,TUE,WED,THU,FRI'
            : repeatType === 'WEEKENDS'
            ? 'SAT,SUN'
            : null,
        repeatDates: repeatType === 'MONTHLY_DATE' ? repeatDates : null,
        parentId: parentId || null,
        position: initialTask?.position ?? 0,
        userCode,
      };

      await onSave(taskToSave);
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setError(`Failed to save task: ${msg}`);
    } finally {
      setSaving(false);
    }
  };

  // Possible parent candidates: cannot be self or descendant
  const validParents = allTasks.filter(
    (t) => !initialTask || (t.id !== initialTask.id && t.parentId !== initialTask.id)
  );

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 overflow-y-auto">
      <div className="relative w-full max-w-lg rounded-3xl bg-[#1e1f26] border border-[#474556]/60 p-6 shadow-2xl animate-fade-in my-8">
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-[#474556]/40">
          <h3 className="text-xl font-bold tracking-tight text-[#e3e1ec]">
            {isEditing ? 'Edit Task' : 'New Task'}
          </h3>
          <button
            onClick={onClose}
            className="flex h-8 w-8 items-center justify-center rounded-full bg-[#12131a] text-[#918ea2] hover:text-white transition-colors cursor-pointer"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {error && (
          <div className="mt-4 rounded-xl bg-[#93000a]/40 border border-[#ffb4ab]/30 p-3 text-xs text-[#ffdad6]">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-5 space-y-4 text-sm">
          {/* Title */}
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-[#c8c4d9] mb-1.5">
              Task Title *
            </label>
            <input
              type="text"
              required
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="What needs to be done?"
              className="w-full rounded-2xl bg-[#12131a] border border-[#474556] p-3.5 text-[#e3e1ec] focus:outline-none focus:border-[#c5c0ff] focus:ring-1 focus:ring-[#5a45ff]"
              autoFocus
            />
          </div>

          {/* Notes */}
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-[#c8c4d9] mb-1.5">
              Notes / Description
            </label>
            <textarea
              rows={2}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Add details, links, or instructions..."
              className="w-full rounded-2xl bg-[#12131a] border border-[#474556] p-3 text-[#e3e1ec] focus:outline-none focus:border-[#c5c0ff] resize-none"
            />
          </div>

          {/* Priority */}
          <div>
            <label className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-[#c8c4d9] mb-1.5">
              <Flag className="h-3.5 w-3.5 text-[#c5c0ff]" />
              <span>Priority</span>
            </label>
            <div className="grid grid-cols-4 gap-2">
              {(['LOW', 'MEDIUM', 'HIGH', 'URGENT'] as Priority[]).map((p) => (
                <button
                  key={p}
                  type="button"
                  onClick={() => setPriority(p)}
                  className={`py-2 rounded-xl text-xs font-bold transition-all cursor-pointer ${
                    priority === p
                      ? p === 'URGENT'
                        ? 'bg-[#ffb4ab]/20 text-[#ffb4ab] border border-[#ffb4ab]'
                        : p === 'HIGH'
                        ? 'bg-[#ffb77d]/20 text-[#ffb77d] border border-[#ffb77d]'
                        : 'bg-[#5a45ff] text-white shadow-md'
                      : 'bg-[#12131a] text-[#918ea2] border border-[#474556]/60 hover:border-[#918ea2]'
                  }`}
                >
                  {p}
                </button>
              ))}
            </div>
          </div>

          {/* Due Date & Time */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-[#c8c4d9] mb-1.5">
                <Calendar className="h-3.5 w-3.5 text-[#c5c0ff]" />
                <span>Due Date</span>
              </label>
              <div className="flex gap-1.5 mb-2">
                <button
                  type="button"
                  onClick={() => {
                    setDueDate('Today');
                    setIsCustomDate(false);
                  }}
                  className={`flex-1 py-1.5 rounded-lg text-xs font-semibold cursor-pointer ${
                    !isCustomDate && dueDate === 'Today'
                      ? 'bg-[#5a45ff] text-white'
                      : 'bg-[#12131a] text-[#918ea2] border border-[#474556]'
                  }`}
                >
                  Today
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setDueDate('Tomorrow');
                    setIsCustomDate(false);
                  }}
                  className={`flex-1 py-1.5 rounded-lg text-xs font-semibold cursor-pointer ${
                    !isCustomDate && dueDate === 'Tomorrow'
                      ? 'bg-[#5a45ff] text-white'
                      : 'bg-[#12131a] text-[#918ea2] border border-[#474556]'
                  }`}
                >
                  Tmrw
                </button>
                <button
                  type="button"
                  onClick={() => setIsCustomDate(true)}
                  className={`flex-1 py-1.5 rounded-lg text-xs font-semibold cursor-pointer ${
                    isCustomDate
                      ? 'bg-[#5a45ff] text-white'
                      : 'bg-[#12131a] text-[#918ea2] border border-[#474556]'
                  }`}
                >
                  Pick
                </button>
              </div>
              {isCustomDate && (
                <input
                  type="date"
                  value={customDateVal}
                  onChange={(e) => setCustomDateVal(e.target.value)}
                  className="w-full rounded-xl bg-[#12131a] border border-[#474556] p-2 text-xs text-[#e3e1ec]"
                />
              )}
            </div>

            <div>
              <label className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-[#c8c4d9] mb-1.5">
                <Clock className="h-3.5 w-3.5 text-[#c5c0ff]" />
                <span>Due Time</span>
              </label>
              <input
                type="text"
                value={dueTime}
                onChange={(e) => setDueTime(e.target.value)}
                placeholder="e.g. 06:30 PM"
                className="w-full rounded-2xl bg-[#12131a] border border-[#474556] p-2.5 text-sm text-[#e3e1ec] focus:outline-none focus:border-[#c5c0ff]"
              />
            </div>
          </div>

          {/* Repeat */}
          <div>
            <label className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-[#c8c4d9] mb-1.5">
              <Repeat className="h-3.5 w-3.5 text-[#c5c0ff]" />
              <span>Repeat Schedule</span>
            </label>
            <select
              value={repeatType}
              onChange={(e) => setRepeatType(e.target.value as RepeatType)}
              className="w-full rounded-2xl bg-[#12131a] border border-[#474556] p-3 text-sm text-[#e3e1ec] focus:outline-none focus:border-[#c5c0ff]"
            >
              <option value="NONE">None (Only for now)</option>
              <option value="DAILY">Daily (Every day)</option>
              <option value="WEEKDAYS">Weekdays (Mon - Fri)</option>
              <option value="WEEKENDS">Weekends (Sat - Sun)</option>
              <option value="MONTHLY_DATE">Specific Dates Every Month</option>
            </select>
            {repeatType === 'MONTHLY_DATE' && (
              <input
                type="text"
                value={repeatDates}
                onChange={(e) => setRepeatDates(e.target.value)}
                placeholder="Dates separated by commas, e.g. 5, 20"
                className="mt-2 w-full rounded-xl bg-[#12131a] border border-[#474556] p-2 text-xs text-[#e3e1ec]"
              />
            )}
          </div>

          {/* Tone & Location Grid */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-[#c8c4d9] mb-1.5">
                <Bell className="h-3.5 w-3.5 text-[#c5c0ff]" />
                <span>Alarm Tone</span>
              </label>
              <select
                value={tone}
                onChange={(e) => setTone(e.target.value)}
                className="w-full rounded-2xl bg-[#12131a] border border-[#474556] p-2.5 text-xs text-[#e3e1ec] focus:outline-none"
              >
                {TONES.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-[#c8c4d9] mb-1.5">
                <MapPin className="h-3.5 w-3.5 text-[#68dba9]" />
                <span>Location</span>
              </label>
              <input
                type="text"
                value={location}
                onChange={(e) => setLocation(e.target.value)}
                placeholder="Office, Home, Gym..."
                className="w-full rounded-2xl bg-[#12131a] border border-[#474556] p-2.5 text-xs text-[#e3e1ec]"
              />
            </div>
          </div>

          {/* Parent Task Selector */}
          <div>
            <label className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-[#c8c4d9] mb-1.5">
              <FolderTree className="h-3.5 w-3.5 text-[#c5c0ff]" />
              <span>Parent Task (Hierarchy)</span>
            </label>
            <select
              value={parentId || ''}
              onChange={(e) => setParentId(e.target.value ? e.target.value : null)}
              className="w-full rounded-2xl bg-[#12131a] border border-[#474556] p-3 text-xs text-[#e3e1ec] focus:outline-none"
            >
              <option value="">None (Top-level Task)</option>
              {validParents.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.title}
                </option>
              ))}
            </select>
          </div>

          {/* Save Action */}
          <div className="pt-4 flex items-center justify-end gap-3 border-t border-[#474556]/40">
            <button
              type="button"
              onClick={onClose}
              className="px-5 py-2.5 rounded-2xl text-xs font-semibold text-[#918ea2] hover:text-[#e3e1ec] transition-colors cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={saving}
              className="flex items-center gap-2 rounded-2xl bg-[#5a45ff] hover:bg-[#6853ff] px-6 py-2.5 text-xs font-bold text-white shadow-lg shadow-[#5a45ff]/40 transition-all cursor-pointer disabled:opacity-50"
            >
              <Sparkles className="h-4 w-4" />
              <span>{saving ? 'Saving...' : isEditing ? 'Update Task' : 'Create Task'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
