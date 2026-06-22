package com.postflow.social;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByUserIdAndProvider(Long userId, SocialProvider provider);

    List<SocialAccount> findByUserIdAndProviderOrderByIdAsc(Long userId, SocialProvider provider);

    Optional<SocialAccount> findByUserIdAndProviderAndThreadsUserId(Long userId, SocialProvider provider, String threadsUserId);

    Optional<SocialAccount> findFirstByUserIdAndProviderAndIsDefaultTrue(Long userId, SocialProvider provider);

    long countByUserIdAndProvider(Long userId, SocialProvider provider);

    /** Connected accounts whose token expires before {@code threshold} (refresh candidates). */
    List<SocialAccount> findByStatusAndExpiresAtBefore(ConnectionStatus status, Instant threshold);
}
