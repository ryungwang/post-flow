-- 댓글 자동화 규칙에 대상 SNS(provider) 추가. null = 전체 SNS.
-- 지정 시 한 글이 여러 SNS로 팬아웃돼도 그 플랫폼 채널에만 자동 답글을 단다.
ALTER TABLE comment_rules ADD COLUMN provider VARCHAR(30);
