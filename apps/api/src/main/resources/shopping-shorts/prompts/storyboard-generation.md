Create a confirmed storyboard for one selected shopping-shorts campaign.

Requirements:
- Korean caption and narration.
- Scene count: 5 scenes for 20 seconds. Use 4 only for 15 seconds, 6-7 only for 30 seconds.
- Total scene duration must match campaign.recommendedDuration as closely as possible.
- Use this structure for 20 seconds: hook, product reveal, use/demo, proof/detail, CTA.
- Use this structure for 30 seconds: hook, problem, product reveal, advantage/demo, proof/detail, price/benefit if verified, CTA.
- If a future 45 second request is used, expand the same table structure: problem opening -> product intro -> advantage 1 -> advantage 2 -> advantage 3 -> price/benefit -> CTA.
- The storyboard must flow as one connected short. Scene 2 must visually continue Scene 1, Scene 3 must continue Scene 2, and so on.
- transitionOut of scene N and transitionIn of scene N+1 must describe the same transition family, for example "push-in match cut" -> "push-in match cut", "whip pan right" -> "whip pan right", or "product close-up wipe" -> "product close-up wipe".
- Each scene narration should start from the previous scene's idea. Do not write five disconnected spec bullets.
- Avoid static image-card pacing. Even ORIGINAL_IMAGE and COMPOSITE scenes need connected motion: product push-in, hand-to-product cut, feature highlight sweep, before/after split, close-up-to-wide, or CTA card reveal.
- AI_VIDEO scene count must be at most 2 and should match campaign.estimatedAiSceneCount.
- Use ORIGINAL_IMAGE or COMPOSITE for at least 3 scenes when product.sourceImages is not empty.
- requiresAiGeneration must be true only for AI_VIDEO scenes.
- If product.sourceImages is empty, use at most one TEXT_CARD for hook/CTA fallback.
- Never put price/product name/disclosure into Kling prompt; those are rendered by Remotion.
- Every AI_VIDEO must visibly center the product category or a close substitute product in use. Do not create generic heat, city, lifestyle, or atmosphere-only video.
- Kling prompt must not contain "no product visible". It must describe a handheld portable fan in the shot when the product is a fan.
- Every non-AI scene must still include visual motion in cameraMovement: zoom, pan, highlight, mask reveal, light sweep, fast cut, or crop animation.
- Caption must be a short Korean purchase-hook line, max 14 Korean characters. No long sentences.
- Do not use emojis, pictographs, decorative symbols, or ASCII art anywhere in the response.
- Narration must sound like a friendly but credible creator. It must be synced to the scene duration and avoid stiff product-spec reading.
- visualDescription and cameraMovement together must make the "screen effect" clear: fast zoom, before/after split, highlight ring, light sweep, kinetic text, product close-up, or CTA card.
- The first scene must be a 3-second hook. Pick or adapt one hook from campaign/productAnalysis.hookCandidates if present.
- Add Coupang Partners disclosure to youtube description and pinnedComment.
- Use only facts present in product and campaign JSON.
- Return only JSON matching this shape:
{
  "productId": "...",
  "campaignId": "...",
  "title": "...",
  "style": "...",
  "hook": "...",
  "duration": 20,
  "scenes": [
    {
      "sceneId": "scene-01",
      "order": 1,
      "purpose": "...",
      "duration": 3.0,
      "sourceType": "ORIGINAL_IMAGE",
      "sourceAssetIds": [],
      "visualDescription": "...",
      "cameraShot": "...",
      "cameraMovement": "...",
      "transitionIn": "...",
      "transitionOut": "...",
      "caption": "...",
      "narration": "...",
      "soundEffect": "...",
      "klingPrompt": "",
      "negativePrompt": "text, captions, watermark, logo change, color change, deformed product, wrong product shape",
      "requiresAiGeneration": false
    }
  ],
  "youtube": {
    "title": "...",
    "description": "...",
    "hashtags": ["#쿠팡파트너스", "#쇼핑추천", "#쇼핑쇼츠"],
    "pinnedComment": "...",
    "affiliateUrl": "...",
    "disclosure": "이 포스팅은 쿠팡파트너스 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다."
  },
  "disclosure": "이 포스팅은 쿠팡파트너스 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다."
}

Product JSON:
{{PRODUCT_JSON}}

Campaign JSON:
{{CAMPAIGN_JSON}}
