package com.postflow.social;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Resolves the {@link Publisher} for a given {@link SocialProvider}. */
@Component
public class PublisherRegistry {

    private final Map<SocialProvider, Publisher> byProvider = new EnumMap<>(SocialProvider.class);

    public PublisherRegistry(List<Publisher> publishers) {
        for (Publisher p : publishers) {
            byProvider.put(p.provider(), p);
        }
    }

    public Publisher get(SocialProvider provider) {
        Publisher p = byProvider.get(provider);
        if (p == null) {
            throw new PublishException("지원하지 않는 채널입니다: " + provider);
        }
        return p;
    }
}
