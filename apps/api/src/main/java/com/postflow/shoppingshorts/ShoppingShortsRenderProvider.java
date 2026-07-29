package com.postflow.shoppingshorts;

import java.nio.file.Path;

public interface ShoppingShortsRenderProvider {
    String id();

    RenderResult render(Path campaignDir);

    record RenderResult(
            String provider,
            String status,
            String draftPath,
            String finalPath,
            String thumbnailPath,
            String contactSheetPath,
            String metadataPath,
            double duration,
            int width,
            int height
    ) {
    }
}
