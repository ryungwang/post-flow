import { create } from "zustand";

/** 선택된 Threads 계정(다계정용). null = 기본 계정. 페이지 간 공유. */
type ThreadsAccountState = {
  accountId: number | null;
  setAccountId: (id: number | null) => void;
};

export const useThreadsAccount = create<ThreadsAccountState>((set) => ({
  accountId: null,
  setAccountId: (accountId) => set({ accountId }),
}));
