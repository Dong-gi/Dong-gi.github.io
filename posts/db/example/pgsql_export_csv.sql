CREATE OR REPLACE FUNCTION export_master_tables() RETURNS void AS $$
DECLARE
    master_tables RECORD;
    statement TEXT;
BEGIN
    FOR master_tables IN
        select table_name from information_schema.tables where table_name like 'm_%'
    LOOP
        statement := 'copy ' || master_tables.table_name || ' to ''/home/dgkim/Desktop/' || master_tables.table_name || '.csv'' delimiter '','' csv';
        RAISE NOTICE USING MESSAGE = statement;
        EXECUTE statement;
    END LOOP;
    RETURN;
END;
$$ LANGUAGE plpgsql;

select export_master_tables();