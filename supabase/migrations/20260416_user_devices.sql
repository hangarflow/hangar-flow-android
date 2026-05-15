-- Stores FCM tokens so the backend can target push notifications at
-- specific devices. One row per device; upserted on every cold start.
create table if not exists public.hf_user_devices (
    device_id text primary key,
    user_id uuid references auth.users(id) on delete set null,
    platform text not null default 'android',
    fcm_token text not null default '',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists hf_user_devices_user_idx
    on public.hf_user_devices (user_id);

-- Touch updated_at on every write.
create or replace function public.hf_user_devices_touch() returns trigger as $$
begin new.updated_at := now(); return new; end;
$$ language plpgsql;

drop trigger if exists hf_user_devices_touch_trg on public.hf_user_devices;
create trigger hf_user_devices_touch_trg
    before update on public.hf_user_devices
    for each row execute procedure public.hf_user_devices_touch();

-- RLS: any authenticated user can insert/update their own row.
alter table public.hf_user_devices enable row level security;

create policy hf_user_devices_own on public.hf_user_devices
    for all using (auth.uid() = user_id)
    with check (auth.uid() = user_id);
