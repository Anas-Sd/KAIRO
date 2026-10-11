import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { supabase } from './supabase';
import { Task, TaskDto, TaskSection, Priority, dtoToTask, taskToDto } from './types';
import { determineSection, calculateTriggerMillis } from './utils/dateUtils';

import { LoginModal } from './components/LoginModal';
import { SectionHeader } from './components/SectionHeader';
import { TaskCard } from './components/TaskCard';
import { CreateEditTaskModal } from './components/CreateEditTaskModal';
import { TaskDetailsModal } from './components/TaskDetailsModal';
import { FilterSortBar } from './components/FilterSortBar';
import { BreadcrumbBar } from './components/BreadcrumbBar';

import {
  Plus,
  LogOut,
  Zap,
  CheckCircle2,
  Sparkles,
} from 'lucide-react';

export const App: React.FC = () => {
  // Auth state
  const [userCode, setUserCode] = useState<string | null>(() => localStorage.getItem('kairo_user_code'));
  const [userName, setUserName] = useState<string | null>(() => localStorage.getItem('kairo_user_name') || '');

  // Tasks state
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  // View & Drill-down state
  const [currentParentId, setCurrentParentId] = useState<string | null>(null);

  // Filter & Sort state
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedPriority, setSelectedPriority] = useState<Priority | 'ALL'>('ALL');
  const [sortBy, setSortBy] = useState<'DUE' | 'PRIORITY' | 'TITLE' | 'CREATED'>('DUE');

  // Collapsed sections
  const [collapsedSections, setCollapsedSections] = useState<Record<TaskSection, boolean>>({
    SNOOZED: false,
    OVERDUE: false,
    TODAY: false,
    UPCOMING: false,
    COMPLETED: true, // completed collapsed by default
  });

  // Modals state
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editTaskTarget, setEditTaskTarget] = useState<Task | null>(null);
  const [selectedTaskDetails, setSelectedTaskDetails] = useState<Task | null>(null);

  // Snooze tracking in memory
  const [snoozeMap, setSnoozeMap] = useState<Record<string, number>>({});

  // 1. Fetch Tasks
  const fetchTasks = useCallback(async (isRefresh = false) => {
    if (!userCode) return;
    if (isRefresh) setRefreshing(true);
    else setLoading(true);

    try {
      const { data, error } = await supabase
        .from('tasks')
        .select('*')
        .eq('user_code', userCode)
        .order('position', { ascending: true })
        .order('created_at', { ascending: false });

      if (error) throw error;

      if (data) {
        const domainTasks: Task[] = (data as TaskDto[]).map((dto) => {
          const snoozeUntil = snoozeMap[dto.id];
          const calculatedSection = determineSection(
            dto.due_date,
            dto.due_time,
            dto.due_date_millis,
            dto.is_completed,
            snoozeUntil
          );
          const task = dtoToTask(dto, snoozeUntil);
          task.section = calculatedSection;
          return task;
        });
        setTasks(domainTasks);
      }
    } catch (err) {
      console.error('Error fetching tasks from Supabase:', err);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [userCode, snoozeMap]);

  // Initial load
  useEffect(() => {
    if (userCode) {
      fetchTasks();
    }
  }, [userCode, fetchTasks]);

  // 2. Realtime Supabase Subscription
  useEffect(() => {
    if (!userCode) return;

    const channel = supabase
      .channel('kairo-tasks-changes')
      .on(
        'postgres_changes',
        { event: '*', schema: 'public', table: 'tasks', filter: `user_code=eq.${userCode}` },
        () => {
          fetchTasks(true);
        }
      )
      .subscribe();

    return () => {
      supabase.removeChannel(channel);
    };
  }, [userCode, fetchTasks]);

  // Hierarchy Helpers
  const getDescendantIds = useCallback((taskId: string, all: Task[]): string[] => {
    const result: string[] = [];
    const queue = [taskId];
    while (queue.length > 0) {
      const parentId = queue.shift()!;
      const children = all.filter((t) => t.parentId === parentId);
      for (const child of children) {
        result.push(child.id);
        queue.push(child.id);
      }
    }
    return result;
  }, []);

  const getAncestorIds = useCallback((taskId: string, all: Task[]): string[] => {
    const byId = new Map(all.map((t) => [t.id, t]));
    const ancestors: string[] = [];
    let curr = byId.get(taskId)?.parentId;
    const visited = new Set<string>();
    while (curr && !visited.has(curr)) {
      visited.add(curr);
      ancestors.push(curr);
      curr = byId.get(curr)?.parentId;
    }
    return ancestors;
  }, []);

  // 4. Bi-directional Completion Toggle
  const handleToggleComplete = async (task: Task) => {
    const now = Date.now();
    const toComplete = !task.isCompleted;

    let affectedIds = new Set<string>([task.id]);

    if (toComplete) {
      // 1. Target & descendants become COMPLETED
      getDescendantIds(task.id, tasks).forEach((id) => affectedIds.add(id));

      // 2. Cascade upward: parent completes if all siblings are done
      const byId = new Map(tasks.map((t) => [t.id, { ...t }]));
      affectedIds.forEach((id) => {
        const item = byId.get(id);
        if (item) {
          item.isCompleted = true;
          item.section = 'COMPLETED';
        }
      });

      let currParentId = task.parentId;
      while (currParentId) {
        const parentTask = byId.get(currParentId);
        if (!parentTask) break;
        const siblings = Array.from(byId.values()).filter((t) => t.parentId === currParentId);
        const allSiblingsDone = siblings.every((s) => s.isCompleted || affectedIds.has(s.id));
        if (allSiblingsDone) {
          affectedIds.add(currParentId);
          parentTask.isCompleted = true;
          parentTask.section = 'COMPLETED';
          currParentId = parentTask.parentId;
        } else {
          break;
        }
      }
    } else {
      // Reopening (unticking)
      getDescendantIds(task.id, tasks).forEach((id) => affectedIds.add(id));
      getAncestorIds(task.id, tasks).forEach((id) => affectedIds.add(id));
    }

    // Optimistic UI Update
    const updatedList = tasks.map((t) => {
      if (affectedIds.has(t.id)) {
        const isDone = toComplete;
        const section = isDone
          ? 'COMPLETED'
          : determineSection(t.dueDate, t.dueTime, t.dueDateMillis, false, t.snoozeUntilMillis);
        return {
          ...t,
          isCompleted: isDone,
          section,
          completedAt: isDone ? now : null,
          updatedAt: now,
        };
      }
      return t;
    });

    setTasks(updatedList);

    // Persist to Supabase
    try {
      const affectedTasks = updatedList.filter((t) => affectedIds.has(t.id));
      for (const item of affectedTasks) {
        const dto = taskToDto(item, userCode!);
        await supabase.from('tasks').update(dto).eq('id', item.id);
      }
    } catch (err) {
      console.error('Error saving task completion to Supabase:', err);
      fetchTasks(true);
    }
  };

  // 5. Save Task (Create or Update)
  const handleSaveTask = async (task: Task) => {
    if (!userCode) return;
    const now = Date.now();
    const dto = taskToDto(task, userCode);

    // If added/updated under a completed parent while active, reopen parent and ancestors (Android parity)
    const toReopen = new Set<string>();
    if (task.parentId && !task.isCompleted) {
      const parent = tasks.find((t) => t.id === task.parentId);
      if (parent && parent.isCompleted) {
        toReopen.add(parent.id);
        getAncestorIds(parent.id, tasks).forEach((id) => toReopen.add(id));
      }
    }

    // Optimistic update
    setTasks((prev) => {
      const exists = prev.some((t) => t.id === task.id);
      const base = exists ? prev.map((t) => (t.id === task.id ? task : t)) : [task, ...prev];
      if (toReopen.size === 0) return base;
      return base.map((t) => {
        if (toReopen.has(t.id)) {
          return {
            ...t,
            isCompleted: false,
            section: determineSection(t.dueDate, t.dueTime, t.dueDateMillis, false, t.snoozeUntilMillis),
            completedAt: null,
            updatedAt: now,
          };
        }
        return t;
      });
    });

    try {
      const { error } = await supabase.from('tasks').upsert(dto);
      if (error) throw error;
      for (const rid of toReopen) {
        const item = tasks.find((t) => t.id === rid);
        if (item) {
          const sec = determineSection(item.dueDate, item.dueTime, item.dueDateMillis, false, item.snoozeUntilMillis);
          await supabase.from('tasks').update({
            is_completed: false,
            section: sec,
            completed_at: null,
            updated_at: now,
          }).eq('id', rid);
        }
      }
    } catch (err) {
      console.error('Error upserting task in Supabase:', err);
      fetchTasks(true);
    }
  };

  // 6. Delete Task (Rechecking parent completion)
  const handleDeleteTask = async (taskId: string) => {
    const target = tasks.find((t) => t.id === taskId);
    const parentId = target?.parentId || null;
    const descendantIds = getDescendantIds(taskId, tasks);
    const allToDelete = [taskId, ...descendantIds];
    const now = Date.now();

    // Recheck parent completion after removing children
    setTasks((prev) => {
      let filtered = prev.filter((t) => !allToDelete.includes(t.id));
      if (parentId) {
        const siblings = filtered.filter((t) => t.parentId === parentId);
        if (siblings.length > 0 && siblings.every((s) => s.isCompleted)) {
          filtered = filtered.map((t) => (t.id === parentId ? { ...t, isCompleted: true, section: 'COMPLETED' as const, completedAt: now, updatedAt: now } : t));
        }
      }
      return filtered;
    });

    if (selectedTaskDetails?.id === taskId) {
      setSelectedTaskDetails(null);
    }

    try {
      for (const id of allToDelete) {
        await supabase.from('tasks').delete().eq('id', id);
      }
      if (parentId) {
        const remainingSiblings = tasks.filter((t) => t.parentId === parentId && !allToDelete.includes(t.id));
        if (remainingSiblings.length > 0 && remainingSiblings.every((s) => s.isCompleted)) {
          await supabase.from('tasks').update({ is_completed: true, section: 'COMPLETED', completed_at: now, updated_at: now }).eq('id', parentId);
        }
      }
    } catch (err) {
      console.error('Error deleting task from Supabase:', err);
      fetchTasks(true);
    }
  };

  // 7. Snooze Task
  const handleSnoozeTask = (task: Task, minutes: number) => {
    const snoozeUntil = Date.now() + minutes * 60 * 1000;
    setSnoozeMap((prev) => ({ ...prev, [task.id]: snoozeUntil }));

    setTasks((prev) =>
      prev.map((t) => {
        if (t.id === task.id) {
          return {
            ...t,
            snoozeUntilMillis: snoozeUntil,
            section: 'SNOOZED',
          };
        }
        return t;
      })
    );
  };

  // 8. Add Subtask
  const handleAddSubtask = async (parentTaskId: string, title: string) => {
    if (!userCode) return;
    const parent = tasks.find((t) => t.id === parentTaskId);
    const now = Date.now();
    const siblingCount = tasks.filter((t) => t.parentId === parentTaskId).length;
    const subtask: Task = {
      id: crypto.randomUUID(),
      title,
      notes: null,
      priority: parent?.priority || 'LOW',
      dueDate: parent?.dueDate || 'Today',
      dueDateMillis: parent?.dueDateMillis || now,
      dueTime: parent?.dueTime || null,
      location: null,
      attachmentName: null,
      attachmentUri: null,
      isCompleted: false,
      section: determineSection(parent?.dueDate || 'Today', parent?.dueTime, parent?.dueDateMillis, false),
      createdAt: now,
      updatedAt: now,
      completedAt: null,
      alarmToneUri: null,
      alarmToneTitle: 'Default Tone',
      repeatType: 'NONE',
      repeatDays: null,
      repeatDates: null,
      parentId: parentTaskId,
      position: siblingCount,
      userCode,
    };

    await handleSaveTask(subtask);
  };

  // Subtask stats mapping
  const subtaskStatsMap = useMemo(() => {
    const map = new Map<string, { done: number; total: number }>();
    for (const t of tasks) {
      if (t.parentId) {
        const curr = map.get(t.parentId) || { done: 0, total: 0 };
        curr.total += 1;
        if (t.isCompleted) curr.done += 1;
        map.set(t.parentId, curr);
      }
    }
    return map;
  }, [tasks]);

  // Current view level tasks
  const currentLevelTasks = useMemo(() => {
    return tasks.filter((t) => (currentParentId ? t.parentId === currentParentId : !t.parentId));
  }, [tasks, currentParentId]);

  // Filtered & Sorted tasks
  const filteredTasks = useMemo(() => {
    return currentLevelTasks.filter((t) => {
      // Priority filter
      if (selectedPriority !== 'ALL' && t.priority !== selectedPriority) return false;
      // Search query
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const matchesTitle = t.title.toLowerCase().includes(q);
        const matchesNotes = t.notes?.toLowerCase().includes(q) ?? false;
        const matchesLocation = t.location?.toLowerCase().includes(q) ?? false;
        if (!matchesTitle && !matchesNotes && !matchesLocation) return false;
      }
      return true;
    });
  }, [currentLevelTasks, selectedPriority, searchQuery]);

  // Grouped by section
  const sectionedTasks = useMemo(() => {
    const groups: Record<TaskSection, Task[]> = {
      SNOOZED: [],
      OVERDUE: [],
      TODAY: [],
      UPCOMING: [],
      COMPLETED: [],
    };

    for (const t of filteredTasks) {
      const sec = determineSection(t.dueDate, t.dueTime, t.dueDateMillis, t.isCompleted, t.snoozeUntilMillis);
      groups[sec].push(t);
    }

    // Sort within sections
    const sortFn = (a: Task, b: Task) => {
      switch (sortBy) {
        case 'PRIORITY': {
          const order: Record<Priority, number> = { URGENT: 4, HIGH: 3, MEDIUM: 2, LOW: 1, DUELESS: 0 };
          return order[b.priority] - order[a.priority];
        }
        case 'TITLE':
          return a.title.localeCompare(b.title);
        case 'CREATED':
          return b.createdAt - a.createdAt;
        case 'DUE':
        default: {
          const tA = calculateTriggerMillis(a.dueDate, a.dueTime, a.dueDateMillis) || 0;
          const tB = calculateTriggerMillis(b.dueDate, b.dueTime, b.dueDateMillis) || 0;
          return tA - tB;
        }
      }
    };

    (Object.keys(groups) as TaskSection[]).forEach((k) => groups[k].sort(sortFn));
    return groups;
  }, [filteredTasks, sortBy]);

  const toggleSectionCollapse = (sec: TaskSection) => {
    setCollapsedSections((prev) => ({ ...prev, [sec]: !prev[sec] }));
  };

  const handleLogout = () => {
    localStorage.removeItem('kairo_user_code');
    localStorage.removeItem('kairo_user_name');
    setUserCode(null);
    setTasks([]);
  };

  const currentParentTask = useMemo(() => {
    return currentParentId ? tasks.find((t) => t.id === currentParentId) || null : null;
  }, [tasks, currentParentId]);

  // If not logged in, show access code modal
  if (!userCode) {
    return (
      <LoginModal
        onLoginSuccess={(code, name) => {
          setUserCode(code);
          setUserName(name);
        }}
      />
    );
  }

  return (
    <div className="min-h-screen bg-[#12131a] text-[#e3e1ec] flex flex-col font-sans selection:bg-[#5a45ff]/40">
      {/* Top App Header */}
      <header className="sticky top-0 z-40 bg-[#12131a]/90 backdrop-blur-md border-b border-[#474556]/30 px-4 py-3">
        <div className="max-w-2xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#5a45ff] text-white shadow-md shadow-[#5a45ff]/40">
              <Zap className="h-5 w-5 fill-current" />
            </div>
            <div>
              <h1 className="text-base font-bold tracking-tight text-[#e3e1ec] leading-none">
                KAIRO
              </h1>
              <div className="flex items-center gap-1.5 mt-0.5 text-[11px] text-[#918ea2]">
                <span className="inline-block h-1.5 w-1.5 rounded-full bg-[#68dba9] animate-pulse"></span>
                <span>{userCode}</span>
                <span>•</span>
                <span>{userName}</span>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={handleLogout}
              className="flex items-center gap-1.5 rounded-xl bg-[#1e1f26] border border-[#474556]/40 px-3 py-1.5 text-xs font-semibold text-[#c8c4d9] hover:text-[#ffb4ab] hover:border-[#ffb4ab]/40 transition-colors cursor-pointer"
              title="Switch Workspace"
            >
              <LogOut className="h-3.5 w-3.5" />
              <span>Disconnect</span>
            </button>
          </div>
        </div>
      </header>

      {/* Main Task List Container */}
      <main className="flex-1 max-w-2xl w-full mx-auto px-4 py-4 pb-28">
        {/* Breadcrumb drill-down bar */}
        <BreadcrumbBar
          currentParent={currentParentTask}
          onNavigateHome={() => setCurrentParentId(null)}
        />

        {/* Filter & Sort controls */}
        <FilterSortBar
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          selectedPriority={selectedPriority}
          onPriorityChange={setSelectedPriority}
          sortBy={sortBy}
          onSortChange={setSortBy}
          onRefresh={() => fetchTasks(true)}
          isRefreshing={refreshing}
        />

        {/* Loading state */}
        {loading && (
          <div className="flex flex-col items-center justify-center py-16 text-[#918ea2] gap-2 animate-pulse">
            <Sparkles className="h-6 w-6 text-[#c5c0ff]" />
            <p className="text-xs font-semibold">Syncing tasks from cloud...</p>
          </div>
        )}

        {/* Sections in mobile priority order */}
        {!loading && (
          <div className="space-y-4">
            {/* 1. SNOOZED SECTION */}
            {sectionedTasks.SNOOZED.length > 0 && (
              <div>
                <SectionHeader
                  section="SNOOZED"
                  count={sectionedTasks.SNOOZED.length}
                  isCollapsed={collapsedSections.SNOOZED}
                  onToggle={() => toggleSectionCollapse('SNOOZED')}
                />
                {!collapsedSections.SNOOZED && (
                  <div className="space-y-2">
                    {sectionedTasks.SNOOZED.map((t) => (
                      <TaskCard
                        key={t.id}
                        task={t}
                        subtaskStats={subtaskStatsMap.get(t.id)}
                        onToggleComplete={handleToggleComplete}
                        onClick={setSelectedTaskDetails}
                        onDrillDown={(task) => setCurrentParentId(task.id)}
                      />
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* 2. OVERDUE SECTION */}
            {sectionedTasks.OVERDUE.length > 0 && (
              <div>
                <SectionHeader
                  section="OVERDUE"
                  count={sectionedTasks.OVERDUE.length}
                  isCollapsed={collapsedSections.OVERDUE}
                  onToggle={() => toggleSectionCollapse('OVERDUE')}
                />
                {!collapsedSections.OVERDUE && (
                  <div className="space-y-2">
                    {sectionedTasks.OVERDUE.map((t) => (
                      <TaskCard
                        key={t.id}
                        task={t}
                        subtaskStats={subtaskStatsMap.get(t.id)}
                        onToggleComplete={handleToggleComplete}
                        onClick={setSelectedTaskDetails}
                        onDrillDown={(task) => setCurrentParentId(task.id)}
                      />
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* 3. TODAY SECTION */}
            {sectionedTasks.TODAY.length > 0 && (
              <div>
                <SectionHeader
                  section="TODAY"
                  count={sectionedTasks.TODAY.length}
                  isCollapsed={collapsedSections.TODAY}
                  onToggle={() => toggleSectionCollapse('TODAY')}
                />
                {!collapsedSections.TODAY && (
                  <div className="space-y-2">
                    {sectionedTasks.TODAY.map((t) => (
                      <TaskCard
                        key={t.id}
                        task={t}
                        subtaskStats={subtaskStatsMap.get(t.id)}
                        onToggleComplete={handleToggleComplete}
                        onClick={setSelectedTaskDetails}
                        onDrillDown={(task) => setCurrentParentId(task.id)}
                      />
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* 4. UPCOMING SECTION */}
            {sectionedTasks.UPCOMING.length > 0 && (
              <div>
                <SectionHeader
                  section="UPCOMING"
                  count={sectionedTasks.UPCOMING.length}
                  isCollapsed={collapsedSections.UPCOMING}
                  onToggle={() => toggleSectionCollapse('UPCOMING')}
                />
                {!collapsedSections.UPCOMING && (
                  <div className="space-y-2">
                    {sectionedTasks.UPCOMING.map((t) => (
                      <TaskCard
                        key={t.id}
                        task={t}
                        subtaskStats={subtaskStatsMap.get(t.id)}
                        onToggleComplete={handleToggleComplete}
                        onClick={setSelectedTaskDetails}
                        onDrillDown={(task) => setCurrentParentId(task.id)}
                      />
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* 5. COMPLETED SECTION */}
            {sectionedTasks.COMPLETED.length > 0 && (
              <div>
                <SectionHeader
                  section="COMPLETED"
                  count={sectionedTasks.COMPLETED.length}
                  isCollapsed={collapsedSections.COMPLETED}
                  onToggle={() => toggleSectionCollapse('COMPLETED')}
                />
                {!collapsedSections.COMPLETED && (
                  <div className="space-y-2">
                    {sectionedTasks.COMPLETED.map((t) => (
                      <TaskCard
                        key={t.id}
                        task={t}
                        subtaskStats={subtaskStatsMap.get(t.id)}
                        onToggleComplete={handleToggleComplete}
                        onClick={setSelectedTaskDetails}
                        onDrillDown={(task) => setCurrentParentId(task.id)}
                      />
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* Empty state */}
            {filteredTasks.length === 0 && (
              <div className="flex flex-col items-center justify-center py-20 text-center text-[#918ea2]">
                <div className="flex h-16 w-16 items-center justify-center rounded-3xl bg-[#1e1f26] border border-[#474556]/40 mb-3 text-[#c5c0ff]">
                  <CheckCircle2 className="h-8 w-8 stroke-[1.5]" />
                </div>
                <h3 className="text-base font-semibold text-[#e3e1ec]">No tasks found</h3>
                <p className="mt-1 text-xs text-[#918ea2] max-w-xs">
                  {searchQuery || selectedPriority !== 'ALL'
                    ? 'No tasks match your active filters. Try resetting search or priority.'
                    : 'Tap the + button below to create your first task and alarm.'}
                </p>
              </div>
            )}
          </div>
        )}
      </main>

      {/* Floating Action Button (FAB) */}
      <div className="fixed bottom-6 right-6 z-40 max-w-2xl mx-auto">
        <button
          type="button"
          onClick={() => {
            setEditTaskTarget(null);
            setCreateModalOpen(true);
          }}
          className="flex h-14 w-14 items-center justify-center rounded-2xl bg-[#5a45ff] text-white shadow-2xl shadow-[#5a45ff]/60 hover:bg-[#6853ff] active:scale-95 transition-all cursor-pointer fab-pulse"
          title="Create New Task"
        >
          <Plus className="h-7 w-7 stroke-[2.5]" />
        </button>
      </div>

      {/* Create / Edit Modal */}
      {createModalOpen && (
        <CreateEditTaskModal
          initialTask={editTaskTarget}
          parentTaskId={currentParentId}
          allTasks={tasks}
          userCode={userCode}
          onSave={handleSaveTask}
          onClose={() => {
            setCreateModalOpen(false);
            setEditTaskTarget(null);
          }}
        />
      )}

      {/* Task Details Bottom Sheet / Modal */}
      {selectedTaskDetails && (
        <TaskDetailsModal
          task={selectedTaskDetails}
          subtasks={tasks.filter((t) => t.parentId === selectedTaskDetails.id)}
          parentTask={tasks.find((t) => t.id === selectedTaskDetails.parentId) || null}
          onClose={() => setSelectedTaskDetails(null)}
          onEdit={(t) => {
            setSelectedTaskDetails(null);
            setEditTaskTarget(t);
            setCreateModalOpen(true);
          }}
          onDelete={handleDeleteTask}
          onToggleComplete={handleToggleComplete}
          onAddSubtask={handleAddSubtask}
          onSnooze={handleSnoozeTask}
        />
      )}
    </div>
  );
};
export default App;
