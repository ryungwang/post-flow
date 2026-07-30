-- 발행된 글에 자동으로 다는 '첫 댓글'(제휴 대가성 고지문 등). 발행 후 해당 플랫폼 글에 댓글로 게시된다.
ALTER TABLE posts ADD COLUMN first_comment VARCHAR(1000);
