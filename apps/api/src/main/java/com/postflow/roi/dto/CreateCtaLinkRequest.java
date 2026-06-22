package com.postflow.roi.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCtaLinkRequest(
        @NotBlank String destinationUrl,
        String label
) {
}
