create table if not exists tenants (
  id char(36) primary key,
  slug varchar(64) not null unique,
  name varchar(255) not null,
  modules_json json not null default (json_object()),
  created_at timestamp not null default current_timestamp
);

