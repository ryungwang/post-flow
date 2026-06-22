import { useEffect } from "react";
import { authApi } from "@/lib/auth-api";
import { useAuth } from "@/store/auth";
import { ApiError } from "@/lib/api";

/** On load, if a token exists but the user isn't hydrated, fetch /auth/me. Clears on 401. */
export function useSession() {
  const token = useAuth((s) => s.token);
  const user = useAuth((s) => s.user);
  const setAuth = useAuth((s) => s.setAuth);
  const clear = useAuth((s) => s.clear);

  useEffect(() => {
    if (!token || user) return;
    let active = true;
    authApi
      .me()
      .then((u) => {
        if (active) setAuth(token, u);
      })
      .catch((e) => {
        if (active && e instanceof ApiError && e.status === 401) clear();
      });
    return () => {
      active = false;
    };
  }, [token, user, setAuth, clear]);
}
