create table jpa_gacha (
    gacha_id bigint primary key,
    gacha_name text,
    start_date timestamptz,
    end_date timestamptz
);
create table jpa_gacha_odds (
    gacha_id bigint,
    seq_no int,
    memo text,
    object_id bigint,
    odd int,
    primary key(gacha_id, seq_no)
);