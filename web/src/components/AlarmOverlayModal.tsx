import React, { useEffect } from 'react';
import { BellRing, Clock, X, Volume2 } from 'lucide-react';
import { Task } from '../types';
import { formatTimeDisplay } from '../utils/dateUtils';
import { alarmAudio } from '../utils/audio';

interface AlarmOverlayModalProps {
  task: Task;
  onDismiss: () => void;
  onSnooze: (minutes: number) => void;
}

export const AlarmOverlayModal: React.FC<AlarmOverlayModalProps> = ({
  task,
  onDismiss,
  onSnooze,
}) => {
  useEffect(() => {
    alarmAudio.startAlarm();
    return () => {
      alarmAudio.stopAlarm();
    };
  }, []);

  const handleStop = () => {
    alarmAudio.stopAlarm();
    onDismiss();
  };

  const handleSnooze = (mins: number) => {
    alarmAudio.stopAlarm();
    onSnooze(mins);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/90 backdrop-blur-xl p-4 animate-fade-in select-none">
      <div className="relative w-full max-w-md rounded-3xl bg-[#1e1f26] border-2 border-[#5a45ff] p-8 text-center shadow-[0_0_80px_rgba(90,69,255,0.45)] fab-pulse">
        {/* Animated Ringing Bell */}
        <div className="mx-auto mb-5 flex h-20 w-20 items-center justify-center rounded-3xl bg-[#5a45ff] text-white shadow-xl shadow-[#5a45ff]/50 animate-bounce">
          <BellRing className="h-10 w-10 stroke-[2.5]" />
        </div>

        <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#5a45ff]/20 text-[#c5c0ff] border border-[#5a45ff]/40 text-xs font-bold uppercase tracking-wider mb-2">
          <Volume2 className="h-3.5 w-3.5 animate-pulse" />
          <span>KAIRO ALARM ACTIVE</span>
        </div>

        <h2 className="text-2xl font-black text-[#e3e1ec] leading-tight mt-2 break-words">
          {task.title}
        </h2>

        {task.dueTime && (
          <p className="mt-2 text-sm text-[#c5c0ff] font-semibold flex items-center justify-center gap-1.5">
            <Clock className="h-4 w-4" />
            <span>Scheduled for {formatTimeDisplay(task.dueTime)}</span>
          </p>
        )}

        {task.notes && (
          <p className="mt-3 text-xs text-[#c8c4d9] line-clamp-3 bg-[#12131a] p-3 rounded-2xl border border-[#474556]/40">
            {task.notes}
          </p>
        )}

        {/* Action Buttons */}
        <div className="mt-8 space-y-3">
          <button
            type="button"
            onClick={handleStop}
            className="w-full flex items-center justify-center gap-2 rounded-2xl bg-[#ffb4ab] hover:bg-[#ffdad6] text-[#690005] py-4 px-6 text-base font-bold shadow-xl shadow-[#ffb4ab]/30 active:scale-[0.98] transition-all cursor-pointer"
          >
            <X className="h-5 w-5 stroke-[2.5]" />
            <span>Stop Alarm</span>
          </button>

          <div className="grid grid-cols-2 gap-2.5">
            <button
              type="button"
              onClick={() => handleSnooze(5)}
              className="rounded-2xl bg-[#12131a] hover:bg-[#33343c] border border-[#474556] py-3 text-xs font-semibold text-[#c8c4d9] transition-all cursor-pointer"
            >
              Snooze +5m
            </button>
            <button
              type="button"
              onClick={() => handleSnooze(10)}
              className="rounded-2xl bg-[#12131a] hover:bg-[#33343c] border border-[#474556] py-3 text-xs font-semibold text-[#c8c4d9] transition-all cursor-pointer"
            >
              Snooze +10m
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
