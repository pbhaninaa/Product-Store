alter table shop_settings add column store_name varchar(255) not null default '';
alter table shop_settings add column contact_email varchar(255) not null default '';
alter table shop_settings add column contact_phone varchar(64) not null default '';
alter table shop_settings add column contact_address text not null default '';
alter table shop_settings add column contact_notes text not null default '';
alter table shop_settings add column store_logo_url text not null default '';
alter table shop_settings add column store_hero_url text not null default '';

