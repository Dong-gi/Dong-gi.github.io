-- PostgreSQL 테이블 파티셔닝 기능을 이용하지 않고 수동으로 생성된 파티션 테이블들이 있다고 가정
-- View를 이용하면 편하지만, 사용하지 않고 각 테이블들에 정확한 쿼리를 날려 결과만 합쳐서 보여주는 코드

-- 예. 특정 카드 소지 유저를 추출
CREATE OR REPLACE FUNCTION search_with_any_cards(VARIADIC card_ids BIGINT[]) RETURNS TABLE (
    account_id bigint,
    user_card_id bigint,
    card_id bigint
) AS $$
DECLARE
    table_no integer := 0;
    var_r record;
BEGIN
    WHILE table_no < 100 LOOP
        FOR var_r IN EXECUTE format('SELECT account_id, user_card_id, card_id FROM %I WHERE card_id IN(' || array_to_string(card_ids, ',') || ')', 't_user_card_' || table_no)
        LOOP
            account_id := var_r.account_id;
            user_card_id := var_r.user_card_id;
            card_id := var_r.card_id;
            RETURN NEXT;
        END LOOP;
        table_no := table_no + 1;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- select * from search_with_any_cards(10135, 10136, 10137, 20135, 20136, 20137);
select distinct account_id from search_with_any_cards(10135, 10136, 10137, 20135, 20136, 20137);