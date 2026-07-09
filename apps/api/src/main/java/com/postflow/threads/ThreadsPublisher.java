package com.postflow.threads;

import com.postflow.social.Publisher;
import com.postflow.social.SocialAccount;
import com.postflow.social.SocialAccountRepository;
import com.postflow.social.SocialProvider;
import org.springframework.stereotype.Component;

/** {@link Publisher} for Threads — wraps the existing two-step container publish flow. */
@Component
public class ThreadsPublisher implements Publisher {

    private final SocialAccountRepository repository;
    private final ThreadsPublishService publishService;

    public ThreadsPublisher(SocialAccountRepository repository, ThreadsPublishService publishService) {
        this.repository = repository;
        this.publishService = publishService;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.THREADS;
    }

    @Override
    public String publish(Long accountId, String text, String mediaUrl) {
        SocialAccount account = repository.findById(accountId)
                .orElseThrow(() -> new ThreadsApiException("연결된 Threads 계정을 찾을 수 없어요."));
        return publishService.publish(account.getThreadsUserId(), account.getAccessToken(), text, mediaUrl);
    }
}
