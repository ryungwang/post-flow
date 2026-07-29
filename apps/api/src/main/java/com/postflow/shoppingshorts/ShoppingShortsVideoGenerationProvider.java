package com.postflow.shoppingshorts;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface ShoppingShortsVideoGenerationProvider {
    String id();

    SceneGenerationResult generateScenes(Path campaignDir, List<Map<String, Object>> prompts);

    SceneGenerationResult pollScenes(Path campaignDir);

    SceneGenerationResult downloadScenes(Path campaignDir);

    record SceneGenerationResult(
            String provider,
            int submittedSceneCount,
            int cachedSceneCount,
            List<ShoppingShortsDtos.SceneJob> jobs
    ) {
    }
}
