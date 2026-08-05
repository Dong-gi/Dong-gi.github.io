create table jpa_api_log (
    log_no bigserial,
    user_id bigint not null,
    request_api text not null,
    request_param text,
    api_result int,
    add_date timestamptz
);