-- Run in Supabase → SQL Editor (once per project). Safe to re-run most parts.

create table if not exists public.products (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  price text not null,
  image_url text not null,
  image_path text not null,
  stock integer not null default 0 constraint products_stock_non_negative check (stock >= 0),
  created_at timestamptz not null default now()
);

create index if not exists products_created_at_idx on public.products (created_at desc);

alter table public.products enable row level security;

drop policy if exists "Products are readable by anyone" on public.products;
drop policy if exists "Products are insertable by authenticated users" on public.products;
drop policy if exists "Products are updatable by authenticated users" on public.products;
drop policy if exists "Products are deletable by authenticated users" on public.products;

create policy "Products are readable by anyone"
  on public.products
  for select
  using (true);

create policy "Products are insertable by authenticated users"
  on public.products
  for insert
  with check ((select auth.uid()) is not null);

create policy "Products are updatable by authenticated users"
  on public.products
  for update
  using ((select auth.uid()) is not null);

create policy "Products are deletable by authenticated users"
  on public.products
  for delete
  using ((select auth.uid()) is not null);

-- Realtime: live product list. If you see "already member of publication", skip this line.
alter publication supabase_realtime add table public.products;

-- Next: create Storage bucket `product-images` (public) in the Dashboard, then run supabase/storage-policies.sql
