-- Unify parts inventory across iOS / Android / Desktop on a single table.
--
-- Pre-migration: iOS wrote `hf_inventory_parts`; Android & Desktop wrote
-- `hf_part_locations`. Two split tables, no sync. After this migration,
-- `hf_part_locations` is the canonical home for all platforms.
--
-- This migration is additive only. It adds the iOS-only columns to
-- `hf_part_locations` so iOS can switch to it without losing fidelity.
-- The legacy `hf_inventory_parts` table is left untouched for now —
-- a separate one-time data-copy migration can move existing rows.
--
-- Idempotent: safe to run on any env.

alter table public.hf_part_locations
  add column if not exists vendor_name text,
  add column if not exists vendor_website text,
  add column if not exists vendor_phone text,
  add column if not exists min_stock_qty integer not null default 0,
  add column if not exists needs_urgent_reorder boolean not null default false,
  add column if not exists received_at timestamptz not null default now(),
  add column if not exists plane_model text,
  add column if not exists plane_tail_number text,
  -- iOS lifecycle status: "In Stock" | "Ordered" | "Installed".
  -- Distinct from `stock_status` (ok/low/urgent/order_more) which is supply level.
  add column if not exists status text not null default 'In Stock';
