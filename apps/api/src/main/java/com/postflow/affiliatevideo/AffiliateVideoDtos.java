package com.postflow.affiliatevideo;

/** 제휴 광고영상(6초 1컷) 요청·응답 DTO. */
public final class AffiliateVideoDtos {

    private AffiliateVideoDtos() {
    }

    /**
     * @param productName 제품명(필수)
     * @param features    실제 특징(선택 · 프롬프트 근거)
     * @param hook        훅 문구(선택 · 자막/연출 힌트)
     * @param imageUrl    제품 이미지 URL(필수 · image-to-video 기반)
     */
    public record SubmitRequest(String productName, String features, String hook, String imageUrl) {
    }

    public record SubmitResponse(String jobId, String status, String caption) {
    }

    /** status: SUBMITTED | PROCESSING | READY | FAILED. output이 있으면 영상 다운로드 가능. */
    public record StatusResponse(String status, String error, String output) {
    }
}
