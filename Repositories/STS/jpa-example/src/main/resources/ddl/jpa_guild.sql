create table jpa_guild (
    guild_id bigserial primary key,
    guild_name text not null
);
create table jpa_guild_member (
    guild_id bigint,
    user_id bigint
);