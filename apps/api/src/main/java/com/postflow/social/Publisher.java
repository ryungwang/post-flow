package com.postflow.social;

/**
 * A platform-specific publisher. One implementation per {@link SocialProvider}
 * (ThreadsPublisher, BlueskyPublisher, …). The shared publish pipeline resolves
 * the right one via {@link PublisherRegistry} and calls {@link #publish}.
 */
public interface Publisher {

    SocialProvider provider();

    /**
     * Publish {@code text} (+ optional {@code mediaUrl}) using the connected account.
     * Returns the platform's post id/uri. Throws {@link PublishException} on failure.
     * Loads the account by id so it always uses the freshest stored tokens.
     */
    String publish(Long accountId, String text, String mediaUrl);

    /**
     * Best-effort delete of a previously published post from the platform.
     * {@code platformPostId} is what {@link #publish} returned. Default = unsupported (no-op).
     */
    default void deletePost(Long accountId, String platformPostId) {
        // platforms without delete support do nothing
    }
}
