create table if not exists users (
  id char(36) primary key,
  email varchar(255) not null unique,
  password_hash varchar(255) not null,
  created_at timestamp not null default current_timestamp
);

create table if not exists memberships (
  id char(36) primary key,
  user_id char(36) not null,
  tenant_id char(36) null,
  role varchar(32) not null,
  created_at timestamp not null default current_timestamp,
  constraint memberships_user_fk foreign key (user_id) references users(id) on delete cascade,
  constraint memberships_tenant_fk foreign key (tenant_id) references tenants(id) on delete cascade
);

create index memberships_user_id_idx on memberships (user_id);
create index memberships_tenant_id_idx on memberships (tenant_id);

