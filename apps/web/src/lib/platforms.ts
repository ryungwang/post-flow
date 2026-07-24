/**
 * 콘텐츠 생성 대상 플랫폼. 플랫폼마다 글자수·해시태그·훅 전략이 달라
 * 백엔드(PlatformContentProfile)가 다르게 생성한다. value = SocialProvider enum 이름.
 */
export const GENERATE_PLATFORMS = [
  { value: "THREADS", label: "Threads", hint: "500자 · 해시태그 3~5개 · 대화·답글 유도" },
  { value: "BLUESKY", label: "Bluesky", hint: "300자 엄격 · 해시태그 1~3개 · 간결·임팩트" },
  { value: "MASTODON", label: "Mastodon", hint: "500자 · 해시태그 3~5개(발견 핵심) · 담백·진솔" },
  { value: "INSTAGRAM", label: "Instagram", hint: "이미지 중심 캡션 · 첫 줄 훅 · 해시태그 5~10개" },
  { value: "FACEBOOK", label: "Facebook", hint: "네이티브 선호 · 해시태그 최소 · 친근·공감" },
  { value: "LINKEDIN", label: "LinkedIn", hint: "전문성·인사이트 · 1인칭 관점 · 해시태그 3~5개" },
];
