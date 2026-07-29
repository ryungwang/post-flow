package com.postflow.shoppingshorts;

import org.springframework.util.StringUtils;

import java.util.List;

final class ShoppingShortsTextSanitizer {
    private ShoppingShortsTextSanitizer() {
    }

    static ShoppingShortsDtos.CampaignGenerationResponse sanitize(ShoppingShortsDtos.CampaignGenerationResponse response) {
        ShoppingShortsDtos.ProductAnalysis analysis = response.productAnalysis();
        ShoppingShortsDtos.ProductAnalysis cleanAnalysis = new ShoppingShortsDtos.ProductAnalysis(
                clean(analysis.productId(), 120),
                cleanList(analysis.targetSituations(), 80),
                cleanList(analysis.sellingPoints(), 80),
                cleanList(analysis.riskNotes(), 120),
                cleanList(analysis.recommendedStyles(), 40),
                cleanList(analysis.hookCandidates(), 15),
                analysis.recommendedDuration());
        List<ShoppingShortsDtos.CampaignCandidate> candidates = response.campaignCandidates().stream()
                .map(candidate -> new ShoppingShortsDtos.CampaignCandidate(
                        clean(candidate.campaignId(), 80),
                        clean(candidate.style(), 40),
                        clean(candidate.concept(), 180),
                        clean(candidate.hook(), 34),
                        clean(candidate.targetAudience(), 80),
                        cleanList(candidate.sellingPoints(), 80),
                        clean(candidate.cta(), 60),
                        candidate.recommendedDuration(),
                        candidate.estimatedAiSceneCount(),
                        candidate.originalImageSceneCount(),
                        clean(candidate.estimatedCostLevel(), 20),
                        clean(candidate.accuracyRiskLevel(), 20),
                        candidate.recommendationScore(),
                        clean(candidate.recommendationReason(), 180)))
                .toList();
        return new ShoppingShortsDtos.CampaignGenerationResponse(
                clean(response.productId(), 120),
                cleanAnalysis,
                candidates);
    }

    static ShoppingShortsDtos.StoryboardResponse sanitize(ShoppingShortsDtos.StoryboardResponse response) {
        List<ShoppingShortsDtos.StoryboardScene> scenes = response.scenes().stream()
                .map(scene -> new ShoppingShortsDtos.StoryboardScene(
                        clean(scene.sceneId(), 80),
                        scene.order(),
                        clean(scene.purpose(), 80),
                        scene.duration(),
                        clean(scene.sourceType(), 40),
                        scene.sourceAssetIds() == null ? List.of() : scene.sourceAssetIds(),
                        clean(scene.visualDescription(), 220),
                        clean(scene.cameraShot(), 80),
                        clean(scene.cameraMovement(), 80),
                        clean(scene.transitionIn(), 40),
                        clean(scene.transitionOut(), 40),
                        clean(scene.caption(), 14),
                        clean(scene.narration(), 160),
                        clean(scene.soundEffect(), 40),
                        clean(scene.klingPrompt(), 800),
                        clean(scene.negativePrompt(), 300),
                        scene.requiresAiGeneration()))
                .toList();
        scenes = connectTransitions(scenes);
        ShoppingShortsDtos.YoutubeMetadata youtube = response.youtube();
        ShoppingShortsDtos.YoutubeMetadata cleanYoutube = new ShoppingShortsDtos.YoutubeMetadata(
                clean(youtube.title(), 100),
                clean(youtube.description(), 800),
                cleanList(youtube.hashtags(), 40),
                clean(youtube.pinnedComment(), 500),
                youtube.affiliateUrl(),
                clean(youtube.disclosure(), 200));
        return new ShoppingShortsDtos.StoryboardResponse(
                clean(response.productId(), 120),
                clean(response.campaignId(), 80),
                clean(response.title(), 100),
                clean(response.style(), 40),
                clean(response.hook(), 34),
                response.duration(),
                scenes,
                cleanYoutube,
                clean(response.disclosure(), 200));
    }

    private static List<ShoppingShortsDtos.StoryboardScene> connectTransitions(List<ShoppingShortsDtos.StoryboardScene> scenes) {
        if (scenes.size() < 2) {
            return scenes;
        }
        List<ShoppingShortsDtos.StoryboardScene> connected = new java.util.ArrayList<>(scenes);
        List<String> defaults = List.of(
                "push-in match cut",
                "feature highlight wipe",
                "close-up continuation",
                "CTA card reveal",
                "soft fade");
        for (int i = 0; i < connected.size() - 1; i++) {
            ShoppingShortsDtos.StoryboardScene current = connected.get(i);
            ShoppingShortsDtos.StoryboardScene next = connected.get(i + 1);
            String transition = StringUtils.hasText(current.transitionOut())
                    ? current.transitionOut()
                    : defaults.get(Math.min(i, defaults.size() - 1));
            connected.set(i, withTransitions(current, current.transitionIn(), transition));
            connected.set(i + 1, withTransitions(next, transition, next.transitionOut()));
        }
        ShoppingShortsDtos.StoryboardScene first = connected.getFirst();
        if (!StringUtils.hasText(first.transitionIn())) {
            connected.set(0, withTransitions(first, "Cut", first.transitionOut()));
        }
        ShoppingShortsDtos.StoryboardScene last = connected.getLast();
        if (!StringUtils.hasText(last.transitionOut())) {
            connected.set(connected.size() - 1, withTransitions(last, last.transitionIn(), "End card"));
        }
        return connected;
    }

    private static ShoppingShortsDtos.StoryboardScene withTransitions(
            ShoppingShortsDtos.StoryboardScene scene,
            String transitionIn,
            String transitionOut) {
        return new ShoppingShortsDtos.StoryboardScene(
                scene.sceneId(),
                scene.order(),
                scene.purpose(),
                scene.duration(),
                scene.sourceType(),
                scene.sourceAssetIds(),
                scene.visualDescription(),
                scene.cameraShot(),
                scene.cameraMovement(),
                clean(transitionIn, 40),
                clean(transitionOut, 40),
                scene.caption(),
                scene.narration(),
                scene.soundEffect(),
                scene.klingPrompt(),
                scene.negativePrompt(),
                scene.requiresAiGeneration());
    }

    static String clean(String value, int maxChars) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        String text = value.trim();
        for (int offset = 0; offset < text.length(); ) {
            int cp = text.codePointAt(offset);
            if (isAllowed(cp)) {
                out.appendCodePoint(cp);
            }
            offset += Character.charCount(cp);
        }
        String cleaned = out.toString()
                .replaceAll("\\s+", " ")
                .replaceAll("\\s+([,.!?])", "$1")
                .trim();
        if (maxChars > 0 && cleaned.codePointCount(0, cleaned.length()) > maxChars) {
            return cleaned.substring(0, cleaned.offsetByCodePoints(0, maxChars)).trim();
        }
        return cleaned;
    }

    private static List<String> cleanList(List<String> values, int maxChars) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(value -> clean(value, maxChars))
                .filter(StringUtils::hasText)
                .toList();
    }

    private static boolean isAllowed(int cp) {
        if (cp == 0xFE0F || cp == 0x200D) {
            return false;
        }
        Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
        return block != Character.UnicodeBlock.EMOTICONS
                && block != Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS
                && block != Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS
                && block != Character.UnicodeBlock.TRANSPORT_AND_MAP_SYMBOLS
                && block != Character.UnicodeBlock.SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS
                && block != Character.UnicodeBlock.SYMBOLS_AND_PICTOGRAPHS_EXTENDED_A
                && block != Character.UnicodeBlock.DINGBATS;
    }
}
