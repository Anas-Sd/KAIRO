import React from 'react';
import {
  Check,
  Clock,
  MapPin,
  Bell,
  GitFork,
  ChevronRight,
  Repeat,
} from 'lucide-react';
import { Task } from '../types';
import { formatTimeDisplay } from '../utils/dateUtils';

interface TaskCardProps {
  task: Task;
  subtaskStats?: { done: number; total: number };
  onToggleComplete: (task: Task) => void;
  onClick: (task: Task) => void;
  onDrillDown?: (task: Task) => void;
}

export const TaskCard: React.FC<TaskCardProps> = ({
  task,
  subtaskStats,
  onToggleComplete,
  onClick,
  onDrillDown,
}) => {
  const getPriorityBorder = () => {
    switch (task.priority) {
      case 'URGENT':
        return 'border-l-4 border-l-[#ffb4ab]';
      case 'HIGH':
        return 'border-l-4 border-l-[#ffb77d]';
      case 'MEDIUM':
        return 'border-l-4 border-l-[#c5c0ff]';
      default:
        return 'border-l-4 border-l-transparent';
    }
  };

  const hasSubtasks = subtaskStats && subtaskStats.total > 0;
  const isSnoozed = task.snoozeUntilMillis && task.snoozeUntilMillis > Date.now();

  return (
    <div
      onClick={() => onClick(task)}
      className={`group relative flex flex-col gap-2 rounded-2xl bg-[#1e1f26] border border-[#474556]/40 p-4 transition-all duration-200 hover:border-[#c5c0ff]/30 hover:bg-[#252630] cursor-pointer shadow-md mb-2.5 ${getPriorityBorder()} ${
        task.isCompleted ? 'opacity-60' : 'opacity-100'
      }`}
    >
      <div className="flex items-start gap-3">
        {/* Animated Checkbox */}
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            onToggleComplete(task);
          }}
          className={`mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-lg border transition-all duration-200 ${
            task.isCompleted
              ? 'bg-[#5a45ff] border-[#5a45ff] text-white pop-bounce shadow-md shadow-[#5a45ff]/40'
              : 'border-[#918ea2] hover:border-[#c5c0ff] bg-transparent'
          }`}
        >
          {task.isCompleted && <Check className="h-4 w-4 stroke-[3]" />}
        </button>

        {/* Task Main Content */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <h4
              className={`text-[15px] font-semibold tracking-tight text-[#e3e1ec] leading-snug break-words ${
                task.isCompleted ? 'line-through text-[#918ea2]' : ''
              }`}
            >
              {task.title}
            </h4>

            {/* Repeat icon */}
            {task.repeatType && task.repeatType !== 'NONE' && (
              <span className="flex items-center gap-1 text-[11px] font-medium text-[#c5c0ff] bg-[#5a45ff]/15 px-1.5 py-0.5 rounded-md border border-[#5a45ff]/20">
                <Repeat className="h-3 w-3" />
                <span>{task.repeatType}</span>
              </span>
            )}
          </div>

          {/* Notes Preview */}
          {task.notes && (
            <p className="mt-1 text-xs text-[#c8c4d9] line-clamp-2 leading-relaxed">
              {task.notes}
            </p>
          )}

          {/* Metadata Chips Bar */}
          <div className="mt-2.5 flex items-center gap-2 flex-wrap text-xs text-[#c8c4d9]">
            {/* Time chip */}
            {task.dueTime && (
              <span
                className={`flex items-center gap-1 px-2 py-0.5 rounded-md text-[11px] font-medium ${
                  task.section === 'OVERDUE'
                    ? 'bg-[#ffb4ab]/20 text-[#ffb4ab] border border-[#ffb4ab]/30'
                    : 'bg-[#33343c] text-[#e3e1ec]'
                }`}
              >
                <Clock className="h-3 w-3" />
                <span>{formatTimeDisplay(task.dueTime)}</span>
              </span>
            )}

            {/* Snoozed chip */}
            {isSnoozed && (
              <span className="flex items-center gap-1 px-2 py-0.5 rounded-md text-[11px] font-medium bg-[#ffb77d]/20 text-[#ffb77d] border border-[#ffb77d]/30">
                <Clock className="h-3 w-3" />
                <span>Snoozed</span>
              </span>
            )}

            {/* Tone chip */}
            {task.alarmToneTitle && task.alarmToneTitle !== 'NONE' && (
              <span className="flex items-center gap-1 px-2 py-0.5 rounded-md text-[11px] font-medium bg-[#33343c]/80 text-[#918ea2]">
                <Bell className="h-3 w-3 text-[#c5c0ff]" />
                <span className="truncate max-w-[110px]">{task.alarmToneTitle}</span>
              </span>
            )}

            {/* Location chip */}
            {task.location && (
              <span className="flex items-center gap-1 px-2 py-0.5 rounded-md text-[11px] font-medium bg-[#33343c]/80 text-[#918ea2]">
                <MapPin className="h-3 w-3 text-[#68dba9]" />
                <span className="truncate max-w-[120px]">{task.location}</span>
              </span>
            )}

            {/* Subtasks Progress Pill */}
            {hasSubtasks && (
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  onDrillDown?.(task);
                }}
                className="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-[#5a45ff]/20 text-[#c5c0ff] border border-[#5a45ff]/40 hover:bg-[#5a45ff]/30 transition-colors ml-auto cursor-pointer"
              >
                <GitFork className="h-3 w-3" />
                <span>
                  {subtaskStats.done}/{subtaskStats.total} subtasks
                </span>
                <ChevronRight className="h-3 w-3" />
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
