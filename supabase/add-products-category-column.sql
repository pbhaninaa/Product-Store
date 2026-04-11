-- Run once in Supabase → SQL Editor → New query → Run.
-- Fixes: "Could not find the 'category' column of 'products' in the schema cache"

alter table public.products
  add column if not exists category text not null default 'Uncategorized';

comment on column public.products.category is
  'Display/filter label set by staff when creating the product (e.g. Clothing, Electronics).';

create index if not exists products_category_lower_idx on public.products (lower(category));

-- Tell PostgREST to reload schema so the API sees the new column immediately
notify pgrst, 'reload schema';
