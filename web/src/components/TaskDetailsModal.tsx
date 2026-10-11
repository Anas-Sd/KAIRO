import React, { useState } from 'react';
import {
  X,
  Clock,
  MapPin,
  Bell,
  Repeat,
  Trash2,
  Edit3,
  Plus,
  Check,
  FolderTree,
} from 'lucide-react';
import { Task } from '../types';
import { formatTimeDisplay } from '../utils/dateUtils';

interface TaskDetailsModalProps {
  task: Task;
  subtasks: Task[];
  parentTask?: Task | null;
  onClose: () => void;
  onEdit: (task: Task) => void;
  onDelete: (taskId: string) => void;
  onToggleComplete: (task: Task) => void;
  onAddSubtask: (parentTaskId: string, title: string) => Promise<void>;
  onSnooze: (task: Task, minutes: number) => void;
}

export const TaskDetailsModal: React.FC<TaskDetailsModalProps> = ({
  task,
  subtasks,
  parentTask,
  onClose,
  onEdit,
  onDelete,
  onToggleComplete,
  onAddSubtask,
  onSnooze,
}) => {
  const [newSubtaskTitle, setNewSubtaskTitle] = useState('');
  const [addingSubtask, setAddingSubtask] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  const handleSubtaskSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newSubtaskTitle.trim()) return;

    setAddingSubtask(true);
    try {
      await onAddSubtask(task.id, newSubtaskTitle.trim());
      setNewSubtaskTitle('');
    } finally {
      setAddingSubtask(false);
    }
  };

  const isSnoozed = task.snoozeUntilMillis && task.snoozeUntilMillis > Date.now();

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 overflow-y-auto">
      <div className="relative w-full max-w-lg rounded-3xl bg-[#1e1f26] border border-[#474556]/60 p-6 shadow-2xl animate-fade-in my-8">
        {/* Top actions */}
        <div className="flex items-center justify-between pb-3 border-b border-[#474556]/40">
          <div className="flex items-center gap-2">
            <span
              className={`text-[11px] font-bold px-2.5 py-1 rounded-full uppercase tracking-wider ${
                task.priority === 'URGENT'
                  ? 'bg-[#ffb4ab]/20 text-[#ffb4ab] border border-[#ffb4ab]/30'
                  : task.priority === 'HIGH'
                  ? 'bg-[#ffb77d]/20 text-[#ffb77d] border border-[#ffb77d]/30'
                  : 'bg-[#5a45ff]/20 text-[#c5c0ff] border border-[#5a45ff]/30'
              }`}
            >
              {task.priority} Priority
            </span>

            {parentTask && (
              <span className="flex items-center gap-1 text-[11px] text-[#918ea2] bg-[#12131a] px-2 py-0.5 rounded-md border border-[#474556]/40">
                <FolderTree className="h-3 w-3" />
                <span className="truncate max-w-[120px]">{parentTask.title}</span>
              </span>
            )}
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => onEdit(task)}
              className="flex h-8 w-8 items-center justify-center rounded-full bg-[#12131a] text-[#c5c0ff] hover:bg-[#33343c] transition-colors cursor-pointer"
              title="Edit Task"
            >
              <Edit3 className="h-4 w-4" />
            </button>
            <button
              onClick={() => setShowDeleteConfirm(true)}
              className="flex h-8 w-8 items-center justify-center rounded-full bg-[#12131a] text-[#ffb4ab] hover:bg-[#93000a]/30 transition-colors cursor-pointer"
              title="Delete Task"
            >
              <Trash2 className="h-4 w-4" />
            </button>
            <button
              onClick={onClose}
              className="flex h-8 w-8 items-center justify-center rounded-full bg-[#12131a] text-[#918ea2] hover:text-white transition-colors cursor-pointer"
            >
              <X className="h-5 w-5" />
            </button>
          </div>
        </div>

        {/* Delete confirmation banner */}
        {showDeleteConfirm && (
          <div className="mt-4 rounded-2xl bg-[#93000a]/30 border border-[#ffb4ab]/40 p-4 text-center">
            <p className="text-sm text-[#ffdad6] font-semibold">Delete this task and all its subtasks?</p>
            <div className="mt-3 flex items-center justify-center gap-3">
              <button
                onClick={() => setShowDeleteConfirm(false)}
                className="px-4 py-1.5 rounded-xl text-xs font-semibold bg-[#12131a] text-[#e3e1ec] cursor-pointer"
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  onDelete(task.id);
                  onClose();
                }}
                className="px-4 py-1.5 rounded-xl text-xs font-bold bg-[#ffb4ab] text-[#690005] hover:bg-[#ffdad6] cursor-pointer shadow-md"
              >
                Yes, Delete
              </button>
            </div>
          </div>
        )}

        {/* Title & Completion Button */}
        <div className="mt-4 flex items-start gap-3.5">
          <button
            type="button"
            onClick={() => onToggleComplete(task)}
            className={`mt-1 flex h-7 w-7 shrink-0 items-center justify-center rounded-xl border transition-all duration-200 cursor-pointer ${
              task.isCompleted
                ? 'bg-[#5a45ff] border-[#5a45ff] text-white pop-bounce shadow-md shadow-[#5a45ff]/40'
                : 'border-[#918ea2] hover:border-[#c5c0ff] bg-transparent'
            }`}
          >
            {task.isCompleted && <Check className="h-4 w-4 stroke-[3]" />}
          </button>

          <div>
            <h2
              className={`text-xl font-bold text-[#e3e1ec] leading-snug break-words ${
                task.isCompleted ? 'line-through text-[#918ea2]' : ''
              }`}
            >
              {task.title}
            </h2>
            {task.notes && (
              <p className="mt-2 text-sm text-[#c8c4d9] leading-relaxed whitespace-pre-wrap">
                {task.notes}
              </p>
            )}
          </div>
        </div>

        {/* Details Pills */}
        <div className="mt-4 grid grid-cols-2 gap-2 text-xs">
          <div className="flex items-center gap-2 rounded-xl bg-[#12131a] p-2.5 border border-[#474556]/40">
            <Clock className="h-4 w-4 text-[#c5c0ff]" />
            <div>
              <div className="text-[10px] text-[#918ea2] uppercase font-bold">Due</div>
              <div className="text-[#e3e1ec] font-semibold">
                {task.dueDate} {task.dueTime ? `at ${formatTimeDisplay(task.dueTime)}` : ''}
              </div>
            </div>
          </div>

          <div className="flex items-center gap-2 rounded-xl bg-[#12131a] p-2.5 border border-[#474556]/40">
            <Bell className="h-4 w-4 text-[#ffb77d]" />
            <div>
              <div className="text-[10px] text-[#918ea2] uppercase font-bold">Alarm Tone</div>
              <div className="text-[#e3e1ec] font-semibold truncate max-w-[130px]">
                {task.alarmToneTitle || 'Default Tone'}
              </div>
            </div>
          </div>

          {task.repeatType && task.repeatType !== 'NONE' && (
            <div className="flex items-center gap-2 rounded-xl bg-[#12131a] p-2.5 border border-[#474556]/40">
              <Repeat className="h-4 w-4 text-[#68dba9]" />
              <div>
                <div className="text-[10px] text-[#918ea2] uppercase font-bold">Repeat</div>
                <div className="text-[#e3e1ec] font-semibold">{task.repeatType}</div>
              </div>
            </div>
          )}

          {task.location && (
            <div className="flex items-center gap-2 rounded-xl bg-[#12131a] p-2.5 border border-[#474556]/40">
              <MapPin className="h-4 w-4 text-[#68dba9]" />
              <div>
                <div className="text-[10px] text-[#918ea2] uppercase font-bold">Location</div>
                <div className="text-[#e3e1ec] font-semibold truncate max-w-[130px]">
                  {task.location}
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Snooze Actions Bar */}
        <div className="mt-4 rounded-2xl bg-[#12131a] p-3 border border-[#474556]/40">
          <div className="flex items-center justify-between text-xs mb-2">
            <span className="font-semibold text-[#ffb77d] flex items-center gap-1.5">
              <Clock className="h-3.5 w-3.5" />
              <span>Snooze Alarm</span>
            </span>
            {isSnoozed && (
              <span className="text-[11px] text-[#ffb77d] font-bold">Currently Snoozed</span>
            )}
          </div>
          <div className="grid grid-cols-4 gap-1.5">
            {[5, 10, 15, 30].map((mins) => (
              <button
                key={mins}
                type="button"
                onClick={() => onSnooze(task, mins)}
                className="py-1.5 rounded-lg text-xs font-semibold bg-[#1e1f26] text-[#c8c4d9] hover:bg-[#5a45ff]/20 hover:text-[#c5c0ff] border border-[#474556]/40 transition-colors cursor-pointer"
              >
                +{mins}m
              </button>
            ))}
          </div>
        </div>

        {/* Subtasks Section */}
        <div className="mt-5">
          <div className="flex items-center justify-between mb-2">
            <h4 className="text-xs font-bold uppercase tracking-wider text-[#c8c4d9]">
              Subtasks ({subtasks.filter((s) => s.isCompleted).length}/{subtasks.length})
            </h4>
          </div>

          <div className="space-y-1.5 max-h-48 overflow-y-auto pr-1">
            {subtasks.map((st) => (
              <div
                key={st.id}
                className="flex items-center gap-2.5 rounded-xl bg-[#12131a] p-2.5 border border-[#474556]/30"
              >
                <button
                  type="button"
                  onClick={() => onToggleComplete(st)}
                  className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-md border transition-all cursor-pointer ${
                    st.isCompleted
                      ? 'bg-[#5a45ff] border-[#5a45ff] text-white'
                      : 'border-[#918ea2] bg-transparent'
                  }`}
                >
                  {st.isCompleted && <Check className="h-3 w-3 stroke-[3]" />}
                </button>
                <span
                  className={`text-xs text-[#e3e1ec] flex-1 truncate ${
                    st.isCompleted ? 'line-through text-[#918ea2]' : ''
                  }`}
                >
                  {st.title}
                </span>
              </div>
            ))}
          </div>

          {/* Inline Add Subtask Input */}
          <form onSubmit={handleSubtaskSubmit} className="mt-3 flex gap-2">
            <input
              type="text"
              value={newSubtaskTitle}
              onChange={(e) => setNewSubtaskTitle(e.target.value)}
              placeholder="Add a new subtask..."
              className="flex-1 rounded-xl bg-[#12131a] border border-[#474556] px-3 py-2 text-xs text-[#e3e1ec] focus:outline-none focus:border-[#c5c0ff]"
            />
            <button
              type="submit"
              disabled={addingSubtask || !newSubtaskTitle.trim()}
              className="flex items-center justify-center gap-1 rounded-xl bg-[#5a45ff] px-3.5 py-2 text-xs font-bold text-white shadow-md disabled:opacity-40 cursor-pointer"
            >
              <Plus className="h-3.5 w-3.5" />
              <span>Add</span>
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};
