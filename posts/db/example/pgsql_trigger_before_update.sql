-- 제한 대상 유저 ID 테이블
CREATE TABLE t_restriction_user(user_id bigint primary key);
ALTER TABLE t_restriction_user OWNER TO xxx;
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE t_restriction_user TO yyy;

-- 프로필 갱신 제약 함수
CREATE OR REPLACE FUNCTION restrict_user_profile() RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS(SELECT * FROM t_restriction_user where user_id = NEW.user_id) THEN
        NEW.name = '사용자';
        NEW.message = '안녕하세요';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 프로필 갱신 제약 트리거 설정
CREATE TRIGGER restrict_user_profile_trigger BEFORE UPDATE ON t_user_profile
FOR EACH ROW
EXECUTE PROCEDURE restrict_user_profile();