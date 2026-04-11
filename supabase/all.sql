-- =============================================================================
-- Product Site — full Supabase setup (one file)
-- =============================================================================
-- Run the entire script in: Supabase Dashboard → SQL Editor → New query → Run.
-- Safe to re-run: uses IF NOT EXISTS, DROP IF EXISTS, ON CONFLICT, etc. where possible.
--
-- Orders: stock is reduced only when staff calls confirm_order_payment (not at checkout).
-- If you ever used an older create_order that subtracted stock at checkout, reconcile old unpaid orders first.
--
-- After this:
--   • Authentication → enable Email → add a user for /admin
--   • Table Editor → shop_settings (id = 1): edit delivery fee & EFT text if you like
--   • Project Settings → API: copy URL + anon key into your app's .env
--
-- If any step errors, read the message. Common: "already member of publication" → ignore.
--
-- Existing project missing `products.category`? Section **2b** adds it. The script ends with
-- `notify pgrst, 'reload schema'` so the API picks up new columns (avoids schema cache errors).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1) Products table, RLS, Realtime
-- ---------------------------------------------------------------------------

create table if not exists public.products (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  category text not null default 'Uncategorized',
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

do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'products'
  ) then
    alter publication supabase_realtime add table public.products;
  end if;
end $$;

-- ---------------------------------------------------------------------------
-- 2) Stock column (for databases that had products before stock existed)
-- ---------------------------------------------------------------------------

alter table public.products
  add column if not exists stock integer not null default 0;

comment on column public.products.stock is 'Units available to sell; decremented when orders are placed via create_order.';

alter table public.products drop constraint if exists products_stock_non_negative;
alter table public.products
  add constraint products_stock_non_negative check (stock >= 0);

-- ---------------------------------------------------------------------------
-- 2b) Category (for buyer filters; admin sets when publishing a product)
-- ---------------------------------------------------------------------------

alter table public.products
  add column if not exists category text not null default 'Uncategorized';

comment on column public.products.category is 'Display/filter label set by staff when creating the product (e.g. Clothing, Electronics).';

create index if not exists products_category_lower_idx on public.products (lower(category));

-- ---------------------------------------------------------------------------
-- 3) Shop settings, orders, order items, RPCs, Realtime on orders
-- ---------------------------------------------------------------------------

create table if not exists public.shop_settings (
  id smallint primary key default 1 constraint shop_settings_singleton check (id = 1),
  delivery_fee_zar numeric(12, 2) not null default 50.00,
  eft_bank_instructions text not null default
    'EFT: use your order number as the payment reference. Update this text in shop_settings in Supabase (Table Editor or SQL).'
);

insert into public.shop_settings (id, delivery_fee_zar, eft_bank_instructions)
values (
  1,
  50.00,
  'Pay by EFT using the order reference shown after you place the order. Update bank details here via Supabase → Table Editor → shop_settings.'
)
on conflict (id) do nothing;

alter table public.shop_settings enable row level security;

drop policy if exists "Shop settings are readable by anyone" on public.shop_settings;
create policy "Shop settings are readable by anyone"
  on public.shop_settings
  for select
  using (true);

drop policy if exists "Staff can update shop settings" on public.shop_settings;
create policy "Staff can update shop settings"
  on public.shop_settings
  for update
  using ((select auth.uid()) is not null)
  with check ((select auth.uid()) is not null and id = 1);

-- Standard = flat delivery_fee_zar; per_km = delivery_fee_per_km_zar × distance (km) from checkout
alter table public.shop_settings add column if not exists delivery_fee_mode text;
alter table public.shop_settings add column if not exists delivery_fee_per_km_zar numeric(12, 2);

update public.shop_settings set delivery_fee_mode = 'standard' where delivery_fee_mode is null;
update public.shop_settings set delivery_fee_per_km_zar = 8.00 where delivery_fee_per_km_zar is null;

alter table public.shop_settings alter column delivery_fee_mode set default 'standard';
alter table public.shop_settings alter column delivery_fee_per_km_zar set default 8.00;
alter table public.shop_settings alter column delivery_fee_per_km_zar set not null;

alter table public.shop_settings drop constraint if exists shop_settings_delivery_fee_mode_chk;
alter table public.shop_settings add constraint shop_settings_delivery_fee_mode_chk check (
  delivery_fee_mode in ('standard', 'per_km')
);
alter table public.shop_settings alter column delivery_fee_mode set not null;

comment on column public.shop_settings.delivery_fee_mode is 'standard: flat delivery_fee_zar; per_km: rate × customer distance (km).';
comment on column public.shop_settings.delivery_fee_per_km_zar is 'ZAR per km when delivery_fee_mode = per_km.';

alter table public.shop_settings add column if not exists store_lat double precision;
alter table public.shop_settings add column if not exists store_lng double precision;

comment on column public.shop_settings.store_lat is 'WGS84 latitude — origin for per-km distance (with customer pin on map).';
comment on column public.shop_settings.store_lng is 'WGS84 longitude — origin for per-km distance.';

alter table public.shop_settings add column if not exists bank_name text;
alter table public.shop_settings add column if not exists bank_account_holder text;
alter table public.shop_settings add column if not exists bank_account_number text;
alter table public.shop_settings add column if not exists bank_branch_code text;

comment on column public.shop_settings.bank_name is 'Shown to customers paying by EFT.';
comment on column public.shop_settings.bank_account_holder is 'Account name for EFT.';
comment on column public.shop_settings.bank_account_number is 'Account number for EFT.';
comment on column public.shop_settings.bank_branch_code is 'Branch or universal branch code (optional).';
comment on column public.shop_settings.eft_bank_instructions is 'Extra EFT notes (reference, etc.) shown with bank details.';

alter table public.shop_settings add column if not exists store_name text;

comment on column public.shop_settings.store_name is
  'Public shop / brand name shown in site header, footer, and invoices; set under Store Branding in admin.';

alter table public.shop_settings add column if not exists contact_email text;
alter table public.shop_settings add column if not exists contact_phone text;
alter table public.shop_settings add column if not exists contact_address text;
alter table public.shop_settings add column if not exists contact_notes text;

comment on column public.shop_settings.contact_email is 'Public contact email for /contact page.';
comment on column public.shop_settings.contact_phone is 'Public phone or WhatsApp for /contact page.';
comment on column public.shop_settings.contact_address is 'Physical address or service area (multiline).';
comment on column public.shop_settings.contact_notes is 'Optional: hours, social links, extra copy for Contact page.';

alter table public.shop_settings add column if not exists store_logo_path text;
alter table public.shop_settings add column if not exists store_hero_path text;

comment on column public.shop_settings.store_logo_path is
  'Storage path in product-images bucket (e.g. branding/logo-…); public store icon in app bar.';
comment on column public.shop_settings.store_hero_path is
  'Storage path for wide hero image on customer home page.';

create table if not exists public.orders (
  id uuid primary key default gen_random_uuid(),
  created_at timestamptz not null default now(),
  customer_name text not null,
  customer_email text not null,
  customer_phone text,
  delivery_type text not null constraint orders_delivery_type_chk check (delivery_type in ('pickup', 'delivery')),
  delivery_address text,
  delivery_fee_zar numeric(12, 2) not null default 0,
  payment_method text not null constraint orders_payment_method_chk check (payment_method in ('eft', 'cash_store')),
  payment_confirmed boolean not null default false,
  payment_confirmed_at timestamptz,
  subtotal_zar numeric(12, 2) not null,
  total_zar numeric(12, 2) not null,
  constraint orders_total_chk check (total_zar = subtotal_zar + delivery_fee_zar),
  constraint orders_delivery_address_chk check (
    (delivery_type = 'pickup')
    or (
      delivery_type = 'delivery'
      and delivery_address is not null
      and length(trim(delivery_address)) > 5
    )
  )
);

alter table public.orders add column if not exists cancelled_at timestamptz;

comment on column public.orders.cancelled_at is
  'Staff-cancelled unpaid order; releases lines from availability so other customers can buy. Stock was never reduced.';

-- Fulfillment lifecycle (after payment confirmed, staff advances until completed)
alter table public.orders add column if not exists order_status text;
alter table public.orders add column if not exists completed_at timestamptz;

alter table public.orders add column if not exists delivery_distance_km numeric(12, 3);

comment on column public.orders.delivery_distance_km is 'Km used for per-km pricing; null for pickup or standard flat fee.';

alter table public.orders add column if not exists delivery_lat double precision;
alter table public.orders add column if not exists delivery_lng double precision;

comment on column public.orders.delivery_lat is 'WGS84 latitude of customer delivery pin (per-km mode).';
comment on column public.orders.delivery_lng is 'WGS84 longitude of customer delivery pin (per-km mode).';

comment on column public.orders.order_status is
  'Lifecycle: awaiting_payment, processing, ready, completed, cancelled.';
comment on column public.orders.completed_at is 'Set when order_status becomes completed.';

update public.orders set order_status = 'cancelled' where cancelled_at is not null;
update public.orders set order_status = 'awaiting_payment' where cancelled_at is null and payment_confirmed = false;
update public.orders set order_status = 'processing' where cancelled_at is null and payment_confirmed = true;
update public.orders set order_status = 'awaiting_payment' where order_status is null;

alter table public.orders alter column order_status set default 'awaiting_payment';

alter table public.orders drop constraint if exists orders_order_status_chk;
alter table public.orders add constraint orders_order_status_chk check (
  order_status in ('awaiting_payment', 'processing', 'ready', 'completed', 'cancelled')
);

alter table public.orders alter column order_status set not null;

create index if not exists orders_order_status_idx on public.orders (order_status);

create index if not exists orders_created_at_idx on public.orders (created_at desc);

-- Human-readable order reference (e.g. ORD-000042) for customers, EFT reference, and invoices
alter table public.orders add column if not exists order_ref text;

create sequence if not exists public.orders_reference_seq;

update public.orders o
set order_ref = sub.new_ref
from (
  select id, 'ORD-' || lpad(row_number() over (order by created_at asc)::text, 6, '0') as new_ref
  from public.orders
  where order_ref is null
) sub
where o.id = sub.id;

do $$
declare
  mx bigint;
begin
  select coalesce(max((substring(order_ref from '^ORD-([0-9]+)$'))::bigint), 0)
  into mx
  from public.orders;

  if mx > 0 then
    perform setval('public.orders_reference_seq', mx, true);
  else
    perform setval('public.orders_reference_seq', 1, false);
  end if;
end $$;

create unique index if not exists orders_order_ref_uidx on public.orders (order_ref);

alter table public.orders alter column order_ref set not null;

comment on column public.orders.order_ref is
  'Stable public order number (ORD- + 6 digits). Returned at checkout; UUID remains the primary key.';

create table if not exists public.order_items (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.orders (id) on delete cascade,
  product_id uuid not null references public.products (id),
  quantity int not null constraint order_items_qty_chk check (quantity >= 1 and quantity <= 100),
  unit_price_zar numeric(12, 2) not null,
  line_total_zar numeric(12, 2) not null,
  constraint order_items_line_chk check (line_total_zar = unit_price_zar * quantity)
);

create index if not exists order_items_order_id_idx on public.order_items (order_id);

alter table public.orders enable row level security;
alter table public.order_items enable row level security;

drop policy if exists "Staff can read orders" on public.orders;
create policy "Staff can read orders"
  on public.orders
  for select
  using ((select auth.uid()) is not null);

drop policy if exists "Staff can read order items" on public.order_items;
create policy "Staff can read order items"
  on public.order_items
  for select
  using ((select auth.uid()) is not null);

-- Permanent delete (admin frees storage); line items removed via ON DELETE CASCADE
drop policy if exists "Staff can delete orders" on public.orders;
create policy "Staff can delete orders"
  on public.orders
  for delete
  using ((select auth.uid()) is not null);

drop policy if exists "Staff can delete order items" on public.order_items;
create policy "Staff can delete order items"
  on public.order_items
  for delete
  using ((select auth.uid()) is not null);

drop policy if exists "Staff can update orders" on public.orders;
create policy "Staff can update orders"
  on public.orders
  for update
  using ((select auth.uid()) is not null)
  with check ((select auth.uid()) is not null);

-- Great-circle distance (km), matches client haversine for pricing preview
create or replace function public.haversine_km(
  lat1 double precision,
  lon1 double precision,
  lat2 double precision,
  lon2 double precision
) returns numeric
language plpgsql
immutable
as $$
declare
  r constant double precision := 6371.0;
  dlat double precision;
  dlon double precision;
  a double precision;
  c double precision;
begin
  if lat1 is null or lon1 is null or lat2 is null or lon2 is null then
    return null;
  end if;
  dlat := radians(lat2 - lat1);
  dlon := radians(lon2 - lon1);
  a := sin(dlat / 2) * sin(dlat / 2)
    + cos(radians(lat1)) * cos(radians(lat2)) * sin(dlon / 2) * sin(dlon / 2);
  c := 2 * atan2(sqrt(a), sqrt(1 - a));
  return round((r * c)::numeric, 3);
end;
$$;

drop function if exists public.create_order(text, text, text, text, text, text, jsonb);
drop function if exists public.create_order(text, text, text, text, text, text, jsonb, numeric);
drop function if exists public.create_order(text, text, text, text, text, text, jsonb, double precision, double precision);

create or replace function public.create_order(
  p_customer_name text,
  p_customer_email text,
  p_customer_phone text,
  p_delivery_type text,
  p_delivery_address text,
  p_payment_method text,
  p_items jsonb,
  p_delivery_lat double precision default null,
  p_delivery_lng double precision default null
)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_order uuid;
  v_ref text;
  v_sub numeric(12,2) := 0;
  v_delivery_fee numeric(12,2);
  v_total numeric(12,2);
  v_mode text;
  v_flat numeric(12,2);
  v_per_km numeric(12,2);
  v_dist numeric(12,3);
  v_store_lat double precision;
  v_store_lng double precision;
  agg record;
  v_pid uuid;
  v_qty int;
  v_unit numeric(12,2);
  v_line numeric(12,2);
  v_name text;
  v_email text;
  v_phone text;
  v_stock int;
  v_pending int;
  jline jsonb;
begin
  v_name := trim(coalesce(p_customer_name, ''));
  v_email := lower(trim(coalesce(p_customer_email, '')));
  v_phone := trim(coalesce(p_customer_phone, ''));
  if length(v_name) < 2 then
    raise exception 'invalid_name';
  end if;
  if v_email !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' then
    raise exception 'invalid_email';
  end if;
  if length(v_phone) < 8 or length(v_phone) > 32 then
    raise exception 'invalid_phone';
  end if;
  if length(regexp_replace(v_phone, '[^0-9]', '', 'g')) < 8 then
    raise exception 'invalid_phone';
  end if;
  if p_delivery_type not in ('pickup', 'delivery') then
    raise exception 'invalid_delivery_type';
  end if;
  if p_payment_method not in ('eft', 'cash_store') then
    raise exception 'invalid_payment_method';
  end if;
  if p_delivery_type = 'delivery' and length(trim(coalesce(p_delivery_address, ''))) < 6 then
    raise exception 'delivery_address_required';
  end if;

  select
    coalesce(delivery_fee_mode, 'standard'),
    coalesce(delivery_fee_zar, 0),
    coalesce(delivery_fee_per_km_zar, 0),
    store_lat,
    store_lng
  into v_mode, v_flat, v_per_km, v_store_lat, v_store_lng
  from shop_settings
  where id = 1;

  if v_mode is null or v_mode not in ('standard', 'per_km') then
    v_mode := 'standard';
  end if;

  if p_delivery_type = 'pickup' then
    v_delivery_fee := 0;
  elsif v_mode = 'per_km' then
    if v_store_lat is null or v_store_lng is null then
      raise exception 'store_location_not_set';
    end if;
    if p_delivery_lat is null or p_delivery_lng is null then
      raise exception 'delivery_location_required';
    end if;
    if p_delivery_lat < -90 or p_delivery_lat > 90 or p_delivery_lng < -180 or p_delivery_lng > 180 then
      raise exception 'invalid_delivery_coordinates';
    end if;
    v_dist := public.haversine_km(v_store_lat, v_store_lng, p_delivery_lat, p_delivery_lng);
    if v_dist is null or v_dist < 0 or v_dist > 20000 then
      raise exception 'invalid_delivery_distance';
    end if;
    if v_per_km < 0 or v_per_km > 100000 then
      raise exception 'invalid_shop_delivery_rate';
    end if;
    v_delivery_fee := round(v_per_km * v_dist, 2);
    if v_delivery_fee < 0 or v_delivery_fee > 999999.99 then
      raise exception 'invalid_delivery_fee_computed';
    end if;
  else
    v_delivery_fee := coalesce(v_flat, 0);
  end if;

  if p_items is null or jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) = 0 then
    raise exception 'empty_cart';
  end if;
  if jsonb_array_length(p_items) > 50 then
    raise exception 'too_many_lines';
  end if;

  for agg in
    select (elem->>'product_id')::uuid as pid, sum((elem->>'quantity')::int)::int as qty_sum
    from jsonb_array_elements(p_items) as elem
    group by 1
  loop
    if agg.pid is null or agg.qty_sum < 1 or agg.qty_sum > 100 then
      raise exception 'invalid_line';
    end if;
    select
      trim(p.price)::numeric,
      coalesce(p.stock, 0),
      coalesce(
        (
          select sum(oi.quantity)::int
          from order_items oi
          join orders o on o.id = oi.order_id
          where oi.product_id = agg.pid
            and o.payment_confirmed = false
            and o.cancelled_at is null
        ),
        0
      )
    into v_unit, v_stock, v_pending
    from products p
    where p.id = agg.pid;

    if not found or v_unit is null then
      raise exception 'product_not_found';
    end if;
    if v_unit < 0 or v_unit > 1000000 then
      raise exception 'invalid_product_price';
    end if;
    if (v_stock - v_pending) < agg.qty_sum then
      raise exception 'insufficient_stock';
    end if;
  end loop;

  for jline in select value from jsonb_array_elements(p_items)
  loop
    v_pid := (jline->>'product_id')::uuid;
    v_qty := (jline->>'quantity')::int;
    if v_pid is null or v_qty is null or v_qty < 1 or v_qty > 100 then
      raise exception 'invalid_line';
    end if;
    select trim(price)::numeric into v_unit from products where id = v_pid;
    if v_unit is null then
      raise exception 'product_not_found';
    end if;
    if v_unit < 0 or v_unit > 1000000 then
      raise exception 'invalid_product_price';
    end if;
    v_line := round(v_unit * v_qty, 2);
    v_sub := v_sub + v_line;
  end loop;

  if v_sub <= 0 then
    raise exception 'empty_cart';
  end if;

  v_total := round(v_sub + v_delivery_fee, 2);

  v_ref := 'ORD-' || lpad(nextval('public.orders_reference_seq')::text, 6, '0');

  insert into orders (
    order_ref,
    customer_name,
    customer_email,
    customer_phone,
    delivery_type,
    delivery_address,
    delivery_distance_km,
    delivery_lat,
    delivery_lng,
    delivery_fee_zar,
    payment_method,
    payment_confirmed,
    order_status,
    subtotal_zar,
    total_zar
  )
  values (
    v_ref,
    v_name,
    v_email,
    v_phone,
    p_delivery_type,
    case when p_delivery_type = 'delivery' then trim(p_delivery_address) else null end,
    case
      when p_delivery_type = 'delivery' and v_mode = 'per_km' then v_dist
      else null
    end,
    case when p_delivery_type = 'delivery' then p_delivery_lat else null end,
    case when p_delivery_type = 'delivery' then p_delivery_lng else null end,
    v_delivery_fee,
    p_payment_method,
    false,
    'awaiting_payment',
    v_sub,
    v_total
  )
  returning id into v_order;

  for jline in select value from jsonb_array_elements(p_items)
  loop
    v_pid := (jline->>'product_id')::uuid;
    v_qty := (jline->>'quantity')::int;
    select round(trim(price)::numeric, 2) into v_unit from products where id = v_pid;
    v_line := round(v_unit * v_qty, 2);
    insert into order_items (order_id, product_id, quantity, unit_price_zar, line_total_zar)
    values (v_order, v_pid, v_qty, v_unit, v_line);
  end loop;

  return v_ref;
end;
$$;

revoke all on function public.create_order(text, text, text, text, text, text, jsonb, double precision, double precision) from public;
grant execute on function public.create_order(text, text, text, text, text, text, jsonb, double precision, double precision) to anon, authenticated;

create or replace function public.confirm_order_payment(p_order_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  r record;
  v_rows int;
  v_updated int;
begin
  if (select auth.uid()) is null then
    raise exception 'not_authenticated';
  end if;
  if p_order_id is null then
    raise exception 'invalid_order';
  end if;

  perform 1
  from orders
  where id = p_order_id
    and payment_confirmed = false
    and cancelled_at is null
    and payment_method in ('eft', 'cash_store')
  for update;

  if not found then
    return false;
  end if;

  for r in
    select product_id, quantity
    from order_items
    where order_id = p_order_id
  loop
    update products
    set stock = stock - r.quantity
    where id = r.product_id and stock >= r.quantity;
    get diagnostics v_rows = row_count;
    if v_rows = 0 then
      raise exception 'insufficient_stock_on_confirm';
    end if;
  end loop;

  update orders
  set
    payment_confirmed = true,
    payment_confirmed_at = now(),
    order_status = 'processing',
    completed_at = null
  where id = p_order_id
    and payment_confirmed = false
    and cancelled_at is null;

  get diagnostics v_updated = row_count;
  return v_updated > 0;
end;
$$;

revoke all on function public.confirm_order_payment(uuid) from public;
grant execute on function public.confirm_order_payment(uuid) to authenticated;

create or replace function public.confirm_eft_payment(p_order_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  return public.confirm_order_payment(p_order_id);
end;
$$;

revoke all on function public.confirm_eft_payment(uuid) from public;
grant execute on function public.confirm_eft_payment(uuid) to authenticated;

create or replace function public.cancel_unpaid_order(p_order_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_updated int;
begin
  if (select auth.uid()) is null then
    raise exception 'not_authenticated';
  end if;
  if p_order_id is null then
    raise exception 'invalid_order';
  end if;

  update orders
  set
    cancelled_at = now(),
    order_status = 'cancelled',
    completed_at = null
  where id = p_order_id
    and payment_confirmed = false
    and cancelled_at is null;

  get diagnostics v_updated = row_count;
  return v_updated > 0;
end;
$$;

revoke all on function public.cancel_unpaid_order(uuid) from public;
grant execute on function public.cancel_unpaid_order(uuid) to authenticated;

do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'orders'
  ) then
    alter publication supabase_realtime add table public.orders;
  end if;
end $$;

-- ---------------------------------------------------------------------------
-- 4) Storage bucket + policies (bucket name must match src/supabase.js)
-- ---------------------------------------------------------------------------

insert into storage.buckets (id, name, public)
values ('product-images', 'product-images', true)
on conflict (id) do update set public = excluded.public, name = excluded.name;

drop policy if exists "Public read product images" on storage.objects;
drop policy if exists "Auth upload product images" on storage.objects;
drop policy if exists "Auth delete product images" on storage.objects;

create policy "Public read product images"
  on storage.objects for select
  using (bucket_id = 'product-images');

create policy "Auth upload product images"
  on storage.objects for insert
  to authenticated
  with check (bucket_id = 'product-images');

create policy "Auth delete product images"
  on storage.objects for delete
  to authenticated
  using (bucket_id = 'product-images');

-- ---------------------------------------------------------------------------
-- PostgREST: reload schema cache (new columns, tables, policies visible to the API)
-- ---------------------------------------------------------------------------

notify pgrst, 'reload schema';

-- =============================================================================
-- Done. Enable Email auth and create an admin user in the Dashboard.
-- =============================================================================
