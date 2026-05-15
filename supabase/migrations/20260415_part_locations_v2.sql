-- Parts Inventory v2 — richer fields for shop-floor inventory.
-- Run this AFTER the initial 20260415_part_locations.sql migration.
alter table public.hf_part_locations
    add column if not exists serial_number text not null default '',
    add column if not exists stock_status text not null default 'ok',
    add column if not exists plane_ids text[] not null default array[]::text[];

-- Helpful for "low / urgent" filters on big inventories.
create index if not exists hf_part_locations_stock_idx
    on public.hf_part_locations (org_id, stock_status);
