-- 제한 대상 유저 ID 테이블 생성
CREATE TABLE t_account_restriction(account_id bigint primary key);
ALTER TABLE t_account_restriction OWNER TO xxx;
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE t_account_restriction TO yyy;

-- 프로필 갱신 제약 함수
CREATE OR REPLACE FUNCTION restrict_user_profile() RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS(SELECT * FROM t_account_restriction where account_id = NEW.account_id) THEN
        NEW.nickname = '사용자';
        NEW.profile_message = '잘 부탁드립니다.';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 프로필 갱신 제약 트리거 설정
CREATE TRIGGER restrict_user_profile_trigger BEFORE UPDATE ON t_account_profile
FOR EACH ROW
EXECUTE PROCEDURE restrict_user_profile();