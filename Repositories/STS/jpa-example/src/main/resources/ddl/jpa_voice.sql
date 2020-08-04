create table jpa_voice1 (
    data_type int not null,
    voice_id bigint primary key,
    message text,
    cue_name text,
    voice_actor text
);
create table jpa_voice2 (
    voice_id bigint primary key,
    message text
);
create table jpa_voice_with_cue2 (
    voice_id bigint primary key,
    cue_name text
);
create table jpa_voice_with_actor2 (
    voice_id bigint primary key,
    voice_actor text
);
create table jpa_voice3 (
    voice_id bigint primary key,
    message text
);
create table jpa_voice_with_cue3 (
    voice_id bigint primary key,
    message text,
    cue_name text
);
create table jpa_voice_with_actor3 (
    voice_id bigint primary key,
    message text,
    voice_actor text
);