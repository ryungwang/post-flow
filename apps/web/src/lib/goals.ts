/** Generation goals — value(영문, 프롬프트용) + label(한글) + 설명 + 홍보 대상 필요도. */
export type GoalNeed = "required" | "helpful" | "optional";

export const GENERATE_GOALS: { value: string; label: string; desc: string; need: GoalNeed }[] = [
  { value: "Engagement", label: "참여 유도", desc: "공감·질문으로 댓글·저장을 유도하는 글", need: "optional" },
  { value: "Followers", label: "팔로워 증가", desc: "계속 보고 싶게 만들어 팔로우를 유도", need: "helpful" },
  { value: "Leads", label: "리드 확보", desc: "무료 자료 등으로 연락처(리드)를 모으는 글", need: "required" },
  { value: "Sales", label: "판매·전환", desc: "이득·증거·CTA로 구매·클릭을 유도", need: "required" },
  { value: "Awareness", label: "인지도", desc: "기억·공유되는 한 줄 메시지로 알리기", need: "helpful" },
  { value: "Personal Branding", label: "퍼스널 브랜딩", desc: "내 경험·전문성으로 신뢰를 쌓는 글", need: "optional" },
  { value: "Fun", label: "재미·바이럴", desc: "위트·밈으로 가볍게 퍼지는 글", need: "optional" },
];
