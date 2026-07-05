/**
 * 사업자·서비스 표시 정보 (전자상거래법 표시사항).
 * 로그인 Fineprint·푸터가 여기서 값을 읽는다. 값 = synub-billing/lib/company.ts 정본과 동일(회사 공통).
 */
export const COMPANY = {
  legalName: "주식회사 신업 (Synub Inc.)",
  enName: "Synub Inc.",
  serviceName: "PostFlow",
  ceo: "김륜광",
  bizRegNo: "701-87-03590",
  mailOrderNo: "2026-전주완산-0504",
  address: "전북특별자치도 전주시 완산구 삼천천변2길 36-16, 4층 404호",
  tel: "010-2105-7767",
  email: "haru@synub.io",
  homepage: "https://synub.io",
} as const;
