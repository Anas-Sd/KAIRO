import React from 'react';
import { ChevronDown, Clock, AlertTriangle, Calendar, CalendarDays, CheckCircle2 } from 'lucide-react';
import { TaskSection } from '../types';

interface SectionHeaderProps {
  section: TaskSection;
  count: number;
  isCollapsed: boolean;
  onToggle: () => void;
}

export const SectionHeader: React.FC<SectionHeaderProps> = ({
  section,
  count,
  isCollapsed,
  onToggle,
}) => {
  const getSectionConfig = () => {
    switch (section) {
      case 'SNOOZED':
        return {
          title: 'Snoozed Tasks',
          icon: Clock,
          colorText: 'text-[#ffb77d]',
          badgeBg: 'bg-[#ffb77d]/20 text-[#ffb77d] border border-[#ffb77d]/30',
        };
      case 'OVERDUE':
        return {
          title: 'Overdue',
          icon: AlertTriangle,
          colorText: 'text-[#ffb4ab]',
          badgeBg: 'bg-[#ffb4ab]/20 text-[#ffb4ab] border border-[#ffb4ab]/30',
        };
      case 'TODAY':
        return {
          title: 'Today',
          icon: Calendar,
          colorText: 'text-[#c5c0ff]',
          badgeBg: 'bg-[#5a45ff]/20 text-[#c5c0ff] border border-[#5a45ff]/30',
        };
      case 'UPCOMING':
        return {
          title: 'Upcoming',
          icon: CalendarDays,
          colorText: 'text-[#ffdcc3]',
          badgeBg: 'bg-[#ffdcc3]/20 text-[#ffdcc3] border border-[#ffdcc3]/30',
        };
      case 'COMPLETED':
        return {
          title: 'Completed',
          icon: CheckCircle2,
          colorText: 'text-[#68dba9]',
          badgeBg: 'bg-[#68dba9]/20 text-[#68dba9] border border-[#68dba9]/30',
        };
    }
  };

  const config = getSectionConfig();
  const Icon = config.icon;

  return (
    <div
      onClick={onToggle}
      className="flex items-center justify-between py-2.5 px-3 rounded-xl hover:bg-[#1e1f26]/60 cursor-pointer select-none transition-colors group mb-1.5"
    >
      <div className="flex items-center gap-2.5">
        <Icon className={`h-4.5 w-4.5 ${config.colorText}`} />
        <h3 className={`text-sm font-semibold tracking-wide ${config.colorText}`}>
          {config.title}
        </h3>
        <span className={`text-[11px] font-bold px-2 py-0.5 rounded-full ${config.badgeBg}`}>
          {count}
        </span>
      </div>

      <ChevronDown
        className={`h-4 w-4 text-[#918ea2] transition-transform duration-200 group-hover:text-[#e3e1ec] ${
          isCollapsed ? '-rotate-90' : 'rotate-0'
        }`}
      />
    </div>
  );
};
