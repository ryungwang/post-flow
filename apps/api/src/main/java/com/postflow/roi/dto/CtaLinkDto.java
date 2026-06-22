package com.postflow.roi.dto;

import com.postflow.roi.CtaLink;

public record CtaLinkDto(
        Long id,
        Long postId,
        String slug,
        String shortUrl,
        String destinationUrl,
        String label
) {
    public static CtaLinkDto from(CtaLink c, String baseUrl) {
        return new CtaLinkDto(c.getId(), c.getPostId(), c.getSlug(),
                baseUrl + "/r/" + c.getSlug(), c.getDestinationUrl(), c.getLabel());
    }
}
