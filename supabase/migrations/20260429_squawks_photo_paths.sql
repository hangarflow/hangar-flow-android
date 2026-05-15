-- Defensive migration: ensure hf_squawks.photo_paths exists.
--
-- iOS, Android, and Desktop clients all write a photo_paths text[] field on
-- hf_squawks (signed-URL keys for photos uploaded to Supabase Storage). The
-- canonical schema file never declared this column, so live envs only have
-- it if it was added through the dashboard. Without it, every squawk write
-- that includes photos returns 400.
--
-- Idempotent: safe to run on any env.

alter table public.hf_squawks
  add column if not exists photo_paths text[] not null default '{}';
