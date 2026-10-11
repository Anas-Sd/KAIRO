import React from 'react';
import { ChevronRight, Home } from 'lucide-react';
import { Task } from '../types';

interface BreadcrumbBarProps {
  currentParent: Task | null;
  onNavigateHome: () => void;
}

export const BreadcrumbBar: React.FC<BreadcrumbBarProps> = ({
  currentParent,
  onNavigateHome,
}) => {
  if (!currentParent) return null;

  return (
    <div className="flex items-center gap-2 py-2 px-3 mb-3 rounded-xl bg-[#5a45ff]/10 border border-[#5a45ff]/30 text-xs">
      <button
        onClick={onNavigateHome}
        className="flex items-center gap-1 font-bold text-[#c5c0ff] hover:underline cursor-pointer"
      >
        <Home className="h-3.5 w-3.5" />
        <span>Tasks</span>
      </button>

      <ChevronRight className="h-3.5 w-3.5 text-[#918ea2]" />

      <span className="font-semibold text-[#e3e1ec] truncate max-w-[200px]">
        {currentParent.title}
      </span>
    </div>
  );
};
