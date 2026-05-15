-- Shared hangar inventory. Every tech can log/update where parts are
-- stored. Everyone in the org sees the same rows in realtime.
create table if not exists public.hf_part_locations (
    id uuid primary key default gen_random_uuid(),
    org_id uuid not null references public.organizations(id) on delete cascade,
    part_name text not null,
    part_number text not null default '',
    location text not null default '',
    quantity integer not null default 1,
    notes text not null default '',
    updated_by_user_id uuid,
    updated_by_user_name text not null default '',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists hf_part_locations_org_idx on public.hf_part_locations (org_id);
create index if not exists hf_part_locations_updated_idx on public.hf_part_locations (org_id, updated_at desc);

-- Keep updated_at fresh on every write.
create or replace function public.hf_part_locations_touch() returns trigger as $$
begin
    new.updated_at := now();
    return new;
end;
$$ language plpgsql;

drop trigger if exists hf_part_locations_touch_trg on public.hf_part_locations;
create trigger hf_part_locations_touch_trg
    before update on public.hf_part_locations
    for each row execute procedure public.hf_part_locations_touch();

-- RLS: any org member can read, insert, update, delete rows in their org.
alter table public.hf_part_locations enable row level security;

drop policy if exists hf_part_locations_select on public.hf_part_locations;
create policy hf_part_locations_select on public.hf_part_locations
    for select using (public.hf_user_in_org(org_id));

drop policy if exists hf_part_locations_insert on public.hf_part_locations;
create policy hf_part_locations_insert on public.hf_part_locations
    for insert with check (public.hf_user_in_org(org_id));

drop policy if exists hf_part_locations_update on public.hf_part_locations;
create policy hf_part_locations_update on public.hf_part_locations
    for update using (public.hf_user_in_org(org_id));

drop policy if exists hf_part_locations_delete on public.hf_part_locations;
create policy hf_part_locations_delete on public.hf_part_locations
    for delete using (public.hf_user_in_org(org_id));
