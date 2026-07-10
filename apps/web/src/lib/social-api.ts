import { api } from "@/lib/api";

/** 크로스-프로바이더 채널(멀티플랫폼). Threads·Bluesky 등 연결된 모든 채널. */
export type Channel = {
  id: number;
  provider: string; // "THREADS" | "BLUESKY"
  username: string | null;
  name: string | null;
  profilePictureUrl: string | null;
  status: string; // CONNECTED / RECONNECT_REQUIRED …
  isDefault: boolean;
};

export const PROVIDER_LABEL: Record<string, string> = {
  THREADS: "Threads",
  BLUESKY: "Bluesky",
  LINKEDIN: "LinkedIn",
  MASTODON: "Mastodon",
};

export const socialApi = {
  /** 연결된 모든 채널(전 플랫폼) — 발행 채널 선택·연결 목록용. */
  channels: () => api.get<Channel[]>("/social/accounts"),
  /** Bluesky 연결 = 핸들 + 앱 비밀번호. 성공 시 갱신된 채널 목록 반환. */
  connectBluesky: (handle: string, appPassword: string) =>
    api.post<Channel[]>("/social/bluesky/connect", { handle, appPassword }),
  /** Mastodon 연결 = 인스턴스 주소 + 액세스 토큰. 성공 시 갱신된 채널 목록 반환. */
  connectMastodon: (instanceUrl: string, accessToken: string) =>
    api.post<Channel[]>("/social/mastodon/connect", { instanceUrl, accessToken }),
  setDefault: (id: number) => api.post<void>(`/social/accounts/${id}/default`),
  disconnect: (id: number) => api.del<void>(`/social/accounts/${id}`),
};
