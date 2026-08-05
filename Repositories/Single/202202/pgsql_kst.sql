CREATE OR REPLACE FUNCTION kst(hour_diff int default 0, minute_diff int default 0) RETURNS timestamp AS $$
BEGIN
    RETURN to_timestamp(extract(epoch from now()) + (hour_diff + 9) * 60 * 60 + minute_diff * 60);
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION kst_midnight(day_diff int default 0) RETURNS date AS $$
BEGIN
    RETURN to_timestamp(extract(epoch from now()) + (day_diff * 24 + 9) * 60 * 60)::date;
END;
$$ LANGUAGE plpgsql;
