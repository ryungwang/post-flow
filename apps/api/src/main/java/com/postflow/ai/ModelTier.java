package com.postflow.ai;

/**
 * Vendor-neutral model tier. Feature code references tiers only; each LLMProvider
 * maps a tier to its own concrete model (see PRD → AI Engine → Provider Abstraction).
 *
 * <ul>
 *   <li>LIGHT    — classification, hashtag extraction (Claude: Haiku 4.5)</li>
 *   <li>STANDARD — bulk single-post generation, default workhorse (Claude: Sonnet 4.6)</li>
 *   <li>PREMIUM  — series planning, premium quality (Claude: Opus 4.8)</li>
 * </ul>
 */
public enum ModelTier {
    LIGHT,
    STANDARD,
    PREMIUM
}
