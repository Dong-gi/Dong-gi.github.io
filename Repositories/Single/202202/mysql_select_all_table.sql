# 전체 유저 테이블 검색(테이블 이름, select 절, 테이블 조건, 전체 조건)

DELIMITER $$
DROP PROCEDURE IF EXISTS select_all_user_tables$$
CREATE PROCEDURE select_all_user_tables(IN _table_name TEXT, IN _select TEXT, IN _after TEXT, IN _all_after TEXT)

BEGIN
    IF LENGTH(_select) < 1 THEN
        SET @_select = '*';
    ELSE
        SET @_select = _select;
    END IF;

    SET @_db_suffix = 1;
    SET @_query = '';

    WHILE @_db_suffix <= 4 DO
        SET @_num = 0;
        WHILE @_num < 8 DO
            SET @_table_suffix = @_db_suffix + 4*@_num;
            SET @_query = CONCAT(@_query, ' UNION (SELECT ', @_select, ' FROM user_', @_db_suffix, '.', _table_name, '_', @_table_suffix, CONCAT(' AS tmp_', @_db_suffix, '_', @_table_suffix), ' ', _after, ')');
            SET @_num = @_num + 1;
        END WHILE;
        SET @_db_suffix = @_db_suffix + 1;
    END WHILE;

    SET @_query = CONCAT('select * from (', SUBSTRING(@_query, 7), ') ', ' AS result ', _all_after);
    /*select @_query; 출력한다*/

    PREPARE stmt1 FROM @_query;
    EXECUTE stmt1;
    DEALLOCATE PREPARE stmt1;
END$$
DELIMITER ;

/* 각 유저 아이템 테이블에서 하나씩 가져오기 */
CALL select_all_user_tables("i_user_item", "*", "limit 1", "");
/* 전체 유저 아이템 테이블에서 10개 가져오기 */
CALL select_all_user_tables("i_user_item", "*", "limit 10", "limit 10");