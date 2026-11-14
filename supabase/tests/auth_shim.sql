create schema if not exists auth;
create table auth.users (id uuid primary key, email text);

create or replace function auth.uid() returns uuid
language sql stable
as $$
  select current_setting('request.jwt.claim.sub', true)::uuid
$$;
