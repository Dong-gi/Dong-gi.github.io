CREATE OR REPLACE FUNCTION print(msg text) RETURNS void AS $$
BEGIN
    RAISE NOTICE USING MESSAGE = msg;
END;
$$ LANGUAGE plpgsql;

select print('Hello World!');