Create product analysis and 3 distinct shopping-shorts campaign candidates for the product JSON below.

Requirements:
- Korean output values.
- Use only facts present in productName, brand, category, price, features, description, options, and sourceImages.
- If features or description are insufficient, include risk notes and choose safer ad styles.
- candidate campaignId values must be stable lowercase kebab-case.
- estimatedAiSceneCount must be 0-2. Do not choose more than 2 paid AI video scenes.
- originalImageSceneCount should normally be 3-5 when product.sourceImages is not empty.
- The best campaign is not the one with more AI scenes. Prefer fewer AI scenes when product images can sell the point.
- estimatedCostLevel must be LOW, MEDIUM, or HIGH.
- accuracyRiskLevel must be LOW, MEDIUM, or HIGH.
- recommendationScore must be 0-100.
- Do not use emojis, pictographs, decorative symbols, or ASCII art anywhere.
- hook must be 34 Korean characters or fewer.
- productAnalysis.hookCandidates must contain exactly 5 short 3-second hooks.
- Each hook candidate must be 15 Korean characters or fewer.
- Include at least one problem hook, one reversal hook, one curiosity hook, one direct-test hook, and one empathy hook.
- Use the strongest hook candidates as campaignCandidates.hook values.
- concept and recommendationReason must be concise enough to scan in a campaign card.
- Return only JSON matching this structure:
{
  "productId": "...",
  "productAnalysis": {
    "productId": "...",
    "targetSituations": [],
    "sellingPoints": [],
    "riskNotes": [],
    "recommendedStyles": [],
    "hookCandidates": ["...", "...", "...", "...", "..."],
    "recommendedDuration": 20
  },
  "campaignCandidates": [
    {
      "campaignId": "...",
      "style": "...",
      "concept": "...",
      "hook": "...",
      "targetAudience": "...",
      "sellingPoints": [],
      "cta": "...",
      "recommendedDuration": 20,
      "estimatedAiSceneCount": 2,
      "originalImageSceneCount": 3,
      "estimatedCostLevel": "HIGH",
      "accuracyRiskLevel": "LOW",
      "recommendationScore": 80,
      "recommendationReason": "..."
    }
  ]
}

Product JSON:
{{PRODUCT_JSON}}
