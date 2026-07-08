import { useEffect } from "react";
import { authApi } from "@/lib/auth-api";
import { getContext, useAuth } from "@/store/auth";
import { ApiError } from "@/lib/api";

// 무폼 자동 로그인은 앱 로드당 1회만(StrictMode 이중 마운트·리렌더로 /auth/refresh가
// 여러 번 도는 것 차단 — 리프레시 회전 재사용 오탐 방지). 로그아웃 후 재시도도 막힘(의도).
let silentRefreshAttempted = false;

/**
 * 통합세션(SSO 공유쿠키) 3단계 훅. 계약 = contracts/SSO_UNIFIED_SESSION.md.
 * ① 로드 시 액세스 토큰 없으면 무폼 `/auth/refresh`(쿠키)로 세션 이어받기
 * ② 토큰 있고 user 없으면 `/auth/me`(선택 컨텍스트) + 컨텍스트 목록 하이드레이션
 * ③ 로드 + 탭 포커스 시 `/auth/session`(회전 없음) 401→로그아웃(네트워크오류·데모 제외)
 */
export function useSession() {
  const token = useAuth((s) => s.token);
  const user = useAuth((s) => s.user);
  const setToken = useAuth((s) => s.setToken);
  const setAuth = useAuth((s) => s.setAuth);
  const setContexts = useAuth((s) => s.setContexts);
  const clear = useAuth((s) => s.clear);

  // ① 무폼 자동 로그인 — 다른 synub 서비스에서 이미 로그인했으면 쿠키로 세션 확립.
  // 앱 로드당 1회만 판단(토큰 유무와 무관하게 플래그를 세운다). 이래야 이후 로그아웃으로
  // 토큰이 비어도 무폼 refresh가 재발화해 "로그아웃 직후 재로그인"되는 레이스가 없다.
  useEffect(() => {
    if (silentRefreshAttempted) return;
    silentRefreshAttempted = true;
    if (token) return; // 이미 로그인 상태면 무폼 refresh 불필요
    // active 취소가드를 쓰지 않는다 — StrictMode 이중 마운트에서 첫 이펙트 cleanup이
    // active=false로 만들고 둘째 실행은 모듈 가드로 early-return해 refresh 결과가 유실된다.
    // 모듈 가드가 이미 1회만 보장하고 앱 루트는 언마운트되지 않으므로 setToken은 안전.
    authApi
      .refresh() // body 없음 → 공유쿠키 기반
      .then((t) => setToken(t.accessToken))
      .catch(() => {
        /* 쿠키 없음/만료 → 비로그인 유지(로그인 폼 노출) */
      });
  }, [token, setToken]);

  // ② /auth/me 하이드레이션.
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

  // ③ 전역 로그아웃 전파 — 로드 + 포커스 시 세션 생존확인. 401만 로그아웃.
  useEffect(() => {
    if (!token || !user || user.demo) return; // 데모/비로그인 제외
    const check = () => {
      authApi
        .session()
        .then((status) => {
          if (status === 401) clear(); // 다른 서비스에서 로그아웃됨 → 로컬 정리(라우트 가드가 /login으로)
        })
        .catch(() => {
          /* 네트워크 오류는 무시(일시 장애로 로그아웃시키지 말 것) */
        });
    };
    check();
    const onFocus = () => {
      if (document.visibilityState === "visible") check();
    };
    window.addEventListener("focus", onFocus);
    document.addEventListener("visibilitychange", onFocus);
    return () => {
      window.removeEventListener("focus", onFocus);
      document.removeEventListener("visibilitychange", onFocus);
    };
  }, [token, user, clear]);
}
