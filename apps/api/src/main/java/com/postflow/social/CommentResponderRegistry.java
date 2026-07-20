package com.postflow.social;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link SocialProvider}별 {@link CommentResponder}를 찾는다.
 *
 * <p>{@link PublisherRegistry}와 달리 없는 플랫폼에 예외를 던지지 않는다 — 댓글 자동응답은
 * 일부 플랫폼만 지원하므로, 미지원은 오류가 아니라 "건너뛸 대상"이다.
 */
@Component
public class CommentResponderRegistry {

    private final Map<SocialProvider, CommentResponder> byProvider = new EnumMap<>(SocialProvider.class);

    public CommentResponderRegistry(List<CommentResponder> responders) {
        for (CommentResponder r : responders) {
            byProvider.put(r.provider(), r);
        }
    }

    public Optional<CommentResponder> find(SocialProvider provider) {
        return Optional.ofNullable(byProvider.get(provider));
    }
}
