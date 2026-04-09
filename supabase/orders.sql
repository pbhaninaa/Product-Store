-- Orders + checkout (run in Supabase SQL Editor AFTER schema.sql).
-- Prices and totals are computed only inside create_order() — clients cannot set amounts.
--
-- Stock: new orders stay payment_confirmed = false until staff runs confirm_order_payment.
-- create_order only checks (stock - qty on other unpaid orders) >= requested qty; it does not reduce stock.
-- If you upgraded from an older create_order that subtracted stock at checkout, reconcile in-flight orders
-- (confirm them or restore stock for unpaid lines) before relying on this logic.

-- ---------------------------------------------------------------------------
-- Shop configuration (delivery fee & EFT copy — edit row id = 1 as needed)
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

-- No insert/update/delete for anon/auth via API — change settings in Dashboard SQL / Table Editor as service role.

-- ---------------------------------------------------------------------------
-- Orders
-- ---------------------------------------------------------------------------
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

create index if not exists orders_created_at_idx on public.orders (created_at desc);

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

-- No INSERT/UPDATE/DELETE policies for anon/auth on these tables — only SECURITY DEFINER RPCs touch rows.

drop policy if exists "Staff can read orders" on public.orders;
create policy "Staff can read orders"
  on public.orders
  for select
  using ((select auth.uid()) is not null);

-- Updates only via confirm_order_payment() (SECURITY DEFINER); no broad UPDATE policy for clients.

drop policy if exists "Staff can read order items" on public.order_items;
create policy "Staff can read order items"
  on public.order_items
  for select
  using ((select auth.uid()) is not null);

-- ---------------------------------------------------------------------------
-- create_order: anon may call; totals from DB product prices only
-- ---------------------------------------------------------------------------
create or replace function public.create_order(
  p_customer_name text,
  p_customer_email text,
  p_customer_phone text,
  p_delivery_type text,
  p_delivery_address text,
  p_payment_method text,
  p_items jsonb
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_order uuid;
  v_sub numeric(12,2) := 0;
  v_delivery_fee numeric(12,2);
  v_total numeric(12,2);
  agg record;
  v_pid uuid;
  v_qty int;
  v_unit numeric(12,2);
  v_line numeric(12,2);
  v_name text;
  v_email text;
  v_stock int;
  v_pending int;
  jline jsonb;
begin
  v_name := trim(coalesce(p_customer_name, ''));
  v_email := lower(trim(coalesce(p_customer_email, '')));
  if length(v_name) < 2 then
    raise exception 'invalid_name';
  end if;
  if v_email !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' then
    raise exception 'invalid_email';
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

  select case when p_delivery_type = 'delivery' then delivery_fee_zar else 0 end
    into v_delivery_fee
  from shop_settings where id = 1;
  if v_delivery_fee is null then
    v_delivery_fee := 0;
  end if;

  if p_items is null or jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) = 0 then
    raise exception 'empty_cart';
  end if;
  if jsonb_array_length(p_items) > 50 then
    raise exception 'too_many_lines';
  end if;

  -- Per product: qty on this order must fit (on-hand stock minus qty already on unpaid orders).
  -- Stock is only reduced when staff confirms payment (confirm_order_payment).
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

  -- Subtotal from DB prices only
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

  insert into orders (
    customer_name,
    customer_email,
    customer_phone,
    delivery_type,
    delivery_address,
    delivery_fee_zar,
    payment_method,
    payment_confirmed,
    subtotal_zar,
    total_zar
  )
  values (
    v_name,
    v_email,
    nullif(trim(coalesce(p_customer_phone, '')), ''),
    p_delivery_type,
    case when p_delivery_type = 'delivery' then trim(p_delivery_address) else null end,
    v_delivery_fee,
    p_payment_method,
    false,
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

  return v_order;
end;
$$;

revoke all on function public.create_order(text, text, text, text, text, text, jsonb) from public;
grant execute on function public.create_order(text, text, text, text, text, text, jsonb) to anon, authenticated;

-- ---------------------------------------------------------------------------
-- confirm_order_payment: staff only — deducts stock then marks order paid (EFT or cash)
-- ---------------------------------------------------------------------------
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
    payment_confirmed_at = now()
  where id = p_order_id
    and payment_confirmed = false
    and cancelled_at is null;

  get diagnostics v_updated = row_count;
  return v_updated > 0;
end;
$$;

revoke all on function public.confirm_order_payment(uuid) from public;
grant execute on function public.confirm_order_payment(uuid) to authenticated;

-- Backwards-compatible name — calls confirm_order_payment
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

-- ---------------------------------------------------------------------------
-- cancel_unpaid_order: staff only — unpaid + not already cancelled
-- ---------------------------------------------------------------------------
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
  set cancelled_at = now()
  where id = p_order_id
    and payment_confirmed = false
    and cancelled_at is null;

  get diagnostics v_updated = row_count;
  return v_updated > 0;
end;
$$;

revoke all on function public.cancel_unpaid_order(uuid) from public;
grant execute on function public.cancel_unpaid_order(uuid) to authenticated;

-- Realtime (safe to re-run)
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
