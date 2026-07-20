-- 댓글 자동응답을 멀티플랫폼으로 일반화.
-- 기존 컬럼은 Threads 전용 이름이었고, 중복 방지 키도 (rule, comment_id)뿐이라
-- 플랫폼이 늘면 서로 다른 플랫폼의 같은 숫자 id가 충돌할 수 있다(둘 다 숫자 문자열).
-- → 컬럼명을 중립화하고 provider를 키에 포함시킨다.

ALTER TABLE comment_replies RENAME COLUMN threads_reply_id TO comment_id;

ALTER TABLE comment_replies ADD COLUMN provider VARCHAR(20);
UPDATE comment_replies SET provider = 'THREADS' WHERE provider IS NULL;
ALTER TABLE comment_replies ALTER COLUMN provider SET NOT NULL;

-- 기존 유니크(rule_id, comment_id) → (rule_id, provider, comment_id)
ALTER TABLE comment_replies DROP CONSTRAINT comment_replies_rule_id_threads_reply_id_key;
ALTER TABLE comment_replies ADD CONSTRAINT ux_comment_replies_rule_provider_comment
    UNIQUE (rule_id, provider, comment_id);
