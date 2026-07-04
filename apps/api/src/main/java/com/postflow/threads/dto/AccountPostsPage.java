package com.postflow.threads.dto;

import java.util.List;

/** 내 게시물 한 페이지 + 다음 커서(null이면 마지막). 무한 스크롤용. */
public record AccountPostsPage(List<ThreadsAccountPostDto> posts, String nextCursor) {
}
