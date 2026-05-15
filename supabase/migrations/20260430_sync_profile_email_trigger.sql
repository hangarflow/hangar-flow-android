-- Keep `hf_user_profiles.email` in sync with `auth.users.email`.
--
-- When a user changes their email through `auth.updateUser` (in-app), the
-- swap on `auth.users.email` only lands after they click the confirmation
-- link Supabase mails to the new address. This trigger fires once that
-- confirmation completes and propagates the new email to the profile row,
-- so Settings / admin Users hub / etc. show the same address the user
-- now logs in with.
--
-- Idempotent: drops the trigger first if it already exists.

create or replace function public.sync_profile_email()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    update public.hf_user_profiles
       set email = new.email,
           updated_at = now()
     where auth_user_id = new.id;
    return new;
end;
$$;

drop trigger if exists on_auth_user_email_change on auth.users;

create trigger on_auth_user_email_change
after update of email on auth.users
for each row
when (new.email is distinct from old.email)
execute function public.sync_profile_email();
