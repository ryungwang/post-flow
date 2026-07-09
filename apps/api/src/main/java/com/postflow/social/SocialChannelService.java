package com.postflow.social;

import com.postflow.social.dto.ChannelDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cross-provider channel management (list / set-default / disconnect) for the multiplatform
 * connect UI and publish picker. Provider-agnostic — no platform API calls. Threads-specific
 * insights/enrichment stay in SocialAccountService (threads package).
 */
@Service
public class SocialChannelService {

    private final SocialAccountRepository repository;

    public SocialChannelService(SocialAccountRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ChannelDto> list(Long userId) {
        return repository.findByUserIdOrderByIdAsc(userId).stream().map(ChannelDto::from).toList();
    }

    @Transactional
    public void setDefault(Long userId, Long accountId) {
        SocialAccount target = repository.findById(accountId)
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("channel not found"));
        for (SocialAccount a : repository.findByUserIdOrderByIdAsc(userId)) {
            a.setDefault(a.getId().equals(target.getId()));
        }
    }

    @Transactional
    public void disconnect(Long userId, Long accountId) {
        SocialAccount account = repository.findById(accountId)
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("channel not found"));
        boolean wasDefault = account.isDefault();
        repository.delete(account); // posts.social_account_id is ON DELETE SET NULL
        repository.flush();
        if (wasDefault) {
            repository.findByUserIdOrderByIdAsc(userId).stream()
                    .findFirst().ifPresent(a -> a.setDefault(true));
        }
    }
}
