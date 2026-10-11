import React from 'react';
import { Search, ArrowUpDown, RotateCw } from 'lucide-react';
import { Priority } from '../types';

interface FilterSortBarProps {
  searchQuery: string;
  onSearchChange: (q: string) => void;
  selectedPriority: Priority | 'ALL';
  onPriorityChange: (p: Priority | 'ALL') => void;
  sortBy: 'DUE' | 'PRIORITY' | 'TITLE' | 'CREATED';
  onSortChange: (sort: 'DUE' | 'PRIORITY' | 'TITLE' | 'CREATED') => void;
  onRefresh: () => void;
  isRefreshing: boolean;
}

export const FilterSortBar: React.FC<FilterSortBarProps> = ({
  searchQuery,
  onSearchChange,
  selectedPriority,
  onPriorityChange,
  sortBy,
  onSortChange,
  onRefresh,
  isRefreshing,
}) => {
  return (
    <div className="space-y-3 mb-4">
      {/* Search and Sort row */}
      <div className="flex items-center gap-2">
        <div className="relative flex-1">
          <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-[#918ea2]" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder="Search tasks, notes, locations..."
            className="w-full rounded-2xl bg-[#1e1f26] border border-[#474556]/60 pl-10 pr-4 py-2.5 text-xs text-[#e3e1ec] focus:outline-none focus:border-[#c5c0ff] placeholder:text-[#918ea2]"
          />
        </div>

        {/* Sort Selector */}
        <div className="relative">
          <select
            value={sortBy}
            onChange={(e) => onSortChange(e.target.value as 'DUE' | 'PRIORITY' | 'TITLE' | 'CREATED')}
            className="appearance-none rounded-2xl bg-[#1e1f26] border border-[#474556]/60 pl-3 pr-8 py-2.5 text-xs font-semibold text-[#c8c4d9] focus:outline-none cursor-pointer"
          >
            <option value="DUE">Sort: Due Time</option>
            <option value="PRIORITY">Sort: Priority</option>
            <option value="TITLE">Sort: Name (A-Z)</option>
            <option value="CREATED">Sort: Newest</option>
          </select>
          <ArrowUpDown className="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-[#918ea2]" />
        </div>

        {/* Refresh Button */}
        <button
          onClick={onRefresh}
          className="flex h-10 w-10 items-center justify-center rounded-2xl bg-[#1e1f26] border border-[#474556]/60 text-[#c8c4d9] hover:text-white transition-colors cursor-pointer"
          title="Refresh from Cloud"
        >
          <RotateCw className={`h-4 w-4 ${isRefreshing ? 'animate-spin text-[#c5c0ff]' : ''}`} />
        </button>
      </div>

      {/* Priority Pill Chips */}
      <div className="flex items-center gap-1.5 overflow-x-auto pb-1 text-xs no-scrollbar">
        {(['ALL', 'URGENT', 'HIGH', 'MEDIUM', 'LOW'] as (Priority | 'ALL')[]).map((p) => (
          <button
            key={p}
            onClick={() => onPriorityChange(p)}
            className={`px-3 py-1 rounded-full font-bold transition-all cursor-pointer whitespace-nowrap ${
              selectedPriority === p
                ? 'bg-[#5a45ff] text-white shadow-md shadow-[#5a45ff]/30'
                : 'bg-[#1e1f26] text-[#918ea2] border border-[#474556]/40 hover:text-[#e3e1ec]'
            }`}
          >
            {p}
          </button>
        ))}
      </div>
    </div>
  );
};
