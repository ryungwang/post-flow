import { useEffect } from "react";
import { authApi } from "@/lib/auth-api";
import { getContext, useAuth } from "@/store/auth";
import { ApiError } from "@/lib/api";

/**
 * On load, if a token exists but the user isn't hydrated, fetch /auth/me (선택 컨텍스트로)
 * + 컨텍스트 목록 로드(스위처용). Clears on 401.
 */
export function useSession() {
  const token = useAuth((s) => s.token);
  const user = useAuth((s) => s.user);
  const setAuth = useAuth((s) => s.setAuth);
  const setContexts = useAuth((s) => s.setContexts);
  const clear = useAuth((s) => s.clear);

  useEffect(() => {
    if (!token || user) return;
    let active = true;
    authApi
      .me(getContext())
      .then((u) => {
        if (active) setAuth(token, u);
      })
      .catch((e) => {
        if (active && e instanceof ApiError && e.status === 401) clear();
      });
    authApi.contexts().then((cs) => { if (active) setContexts(cs); }).catch(() => {});
    return () => {
      active = false;
    };
  }, [token, user, setAuth, setContexts, clear]);
}
