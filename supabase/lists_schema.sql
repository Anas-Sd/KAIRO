-- ==============================================================================
-- KAIRO: Lists & Hierarchical Subtasks Schema for Supabase
-- Run this script in the Supabase SQL Editor
-- ==============================================================================

-- 1. task_lists: Root lists (e.g., Grocery, Project Alpha, Books to read)
CREATE TABLE IF NOT EXISTS public.task_lists (
    id TEXT PRIMARY KEY,
    user_code TEXT NOT NULL,
    name TEXT NOT NULL,
    icon_name TEXT NOT NULL DEFAULT 'list',
    position INT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL DEFAULT (extract(epoch from now()) * 1000)::bigint,
    updated_at BIGINT NOT NULL DEFAULT (extract(epoch from now()) * 1000)::bigint
);

-- 2. list_items: Items and hierarchical subtasks inside a list
CREATE TABLE IF NOT EXISTS public.list_items (
    id TEXT PRIMARY KEY,
    user_code TEXT NOT NULL,
    list_id TEXT NOT NULL REFERENCES public.task_lists(id) ON DELETE CASCADE,
    parent_id TEXT,
    title TEXT NOT NULL,
    icon_name TEXT NOT NULL DEFAULT 'list',
    notes TEXT,
    target_date TEXT,
    target_date_millis BIGINT,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    position INT NOT NULL DEFAULT 0,
    completed_at BIGINT,
    created_at BIGINT NOT NULL DEFAULT (extract(epoch from now()) * 1000)::bigint,
    updated_at BIGINT NOT NULL DEFAULT (extract(epoch from now()) * 1000)::bigint
);

-- 3. Indexes for fast query performance
CREATE INDEX IF NOT EXISTS idx_task_lists_user_code ON public.task_lists(user_code);
CREATE INDEX IF NOT EXISTS idx_task_lists_position ON public.task_lists(position);

CREATE INDEX IF NOT EXISTS idx_list_items_user_code ON public.list_items(user_code);
CREATE INDEX IF NOT EXISTS idx_list_items_list_id ON public.list_items(list_id);
CREATE INDEX IF NOT EXISTS idx_list_items_parent_id ON public.list_items(parent_id);
CREATE INDEX IF NOT EXISTS idx_list_items_position ON public.list_items(position);

-- 4. Enable Row Level Security (RLS) - Permissive for authenticated & anon keys matching project convention
ALTER TABLE public.task_lists ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.list_items ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE tablename = 'task_lists' AND policyname = 'Allow public access to task_lists'
    ) THEN
        CREATE POLICY "Allow public access to task_lists" ON public.task_lists
            FOR ALL USING (true) WITH CHECK (true);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE tablename = 'list_items' AND policyname = 'Allow public access to list_items'
    ) THEN
        CREATE POLICY "Allow public access to list_items" ON public.list_items
            FOR ALL USING (true) WITH CHECK (true);
    END IF;
END $$;
