create table jpa_user1 (
    user_id bigserial primary key,
    nickname text not null,
    add_date timestamptz not null default now(),
    upd_date timestamptz not null default now()
);
create table jpa_user2 (
    user_id bigserial primary key,
    nickname text not null,
    add_date timestamptz not null default now(),
    upd_date timestamptz not null default now()
);
create table jpa_user3 (
    user_id bigserial primary key,
    nickname text not null,
    add_date timestamptz not null default now(),
    upd_date timestamptz not null default now()
);