create table jpa_voice (
    data_type int not null,
    voice_id bigint primary key,
    message text,
    cue_name text,
    voice_actor text
)