/** External policy URLs (hosted on synub-center). Override via env for other domains. */
export const LEGAL = {
  privacy: import.meta.env.VITE_PRIVACY_URL ?? "https://center.synub.com/ko/policies/privacy",
  terms: import.meta.env.VITE_TERMS_URL ?? "https://center.synub.com/ko/policies/terms",
};
