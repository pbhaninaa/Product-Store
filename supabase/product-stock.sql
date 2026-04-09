-- Run in Supabase → SQL Editor if `products` already exists without `stock`.
-- New installs: `schema.sql` already includes `stock`; you can skip this file.

alter table public.products
  add column if not exists stock integer not null default 0;

comment on column public.products.stock is 'Units available to sell; decremented when orders are placed via create_order.';

-- Optional: enforce non-negative stock at DB level (ignore if it already exists)
alter table public.products drop constraint if exists products_stock_non_negative;
alter table public.products
  add constraint products_stock_non_negative check (stock >= 0);
