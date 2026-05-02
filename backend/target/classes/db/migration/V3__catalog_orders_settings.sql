create table if not exists shop_settings (
  id char(36) primary key,
  tenant_id char(36) not null,
  delivery_fee_mode varchar(32) not null default 'standard',
  delivery_fee_flat_zar numeric(12,2) not null default 50.00,
  delivery_fee_per_km_zar numeric(12,2) not null default 8.00,
  store_lat double,
  store_lng double,
  eft_bank_instructions text not null,
  bank_name varchar(255) not null,
  bank_account_holder varchar(255) not null,
  bank_account_number varchar(64) not null,
  bank_branch_code varchar(64) not null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp,
  unique key shop_settings_tenant_unique (tenant_id),
  constraint shop_settings_tenant_fk foreign key (tenant_id) references tenants(id) on delete cascade
);

create table if not exists products (
  id char(36) primary key,
  tenant_id char(36) not null,
  name varchar(255) not null,
  category varchar(255) not null default 'Uncategorized',
  price_zar numeric(12,2) not null,
  image_url text not null,
  image_path text not null,
  stock integer not null default 0,
  archived_at timestamp null,
  created_at timestamp not null default current_timestamp,
  constraint products_tenant_fk foreign key (tenant_id) references tenants(id) on delete cascade
);

create index if not exists products_tenant_created_at_idx on products (tenant_id, created_at desc);
create index if not exists products_tenant_archived_idx on products (tenant_id, archived_at);

create table if not exists orders (
  id char(36) primary key,
  tenant_id char(36) not null,
  created_at timestamp not null default current_timestamp,
  customer_name varchar(255) not null,
  customer_email varchar(255) not null,
  customer_phone varchar(64),
  delivery_type varchar(32) not null,
  delivery_address text,
  delivery_fee_zar numeric(12, 2) not null default 0,
  payment_method varchar(32) not null,
  status varchar(32) not null default 'pending_payment',
  payment_confirmed_at timestamp null,
  cancelled_at timestamp null,
  subtotal_zar numeric(12, 2) not null,
  total_zar numeric(12, 2) not null,
  constraint orders_tenant_fk foreign key (tenant_id) references tenants(id) on delete cascade
);

create index if not exists orders_tenant_created_at_idx on orders (tenant_id, created_at desc);

create table if not exists order_items (
  id char(36) primary key,
  tenant_id char(36) not null,
  order_id char(36) not null,
  product_id char(36) not null,
  quantity int not null,
  unit_price_zar numeric(12, 2) not null,
  line_total_zar numeric(12, 2) not null,
  constraint order_items_tenant_fk foreign key (tenant_id) references tenants(id) on delete cascade,
  constraint order_items_order_fk foreign key (order_id) references orders(id) on delete cascade,
  constraint order_items_product_fk foreign key (product_id) references products(id)
);

create index if not exists order_items_tenant_order_id_idx on order_items (tenant_id, order_id);
create index if not exists order_items_tenant_product_id_idx on order_items (tenant_id, product_id);

