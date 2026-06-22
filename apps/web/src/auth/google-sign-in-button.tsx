import { useEffect, useRef, useState } from "react";

const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID;

export function GoogleSignInButton({
  onCredential,
}: {
  onCredential: (idToken: string) => void;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!CLIENT_ID) return;
    let cancelled = false;

    const init = () => {
      if (cancelled || !window.google || !ref.current) return;
      window.google.accounts.id.initialize({
        client_id: CLIENT_ID,
        callback: (res) => onCredential(res.credential),
        cancel_on_tap_outside: true,
      });
      window.google.accounts.id.renderButton(ref.current, {
        type: "standard",
        theme: "outline",
        size: "large",
        text: "continue_with",
        shape: "pill",
        width: 320,
        locale: "ko",
      });
      setReady(true);
    };

    // GSI script loads async — poll until available.
    if (window.google) {
      init();
    } else {
      const t = setInterval(() => {
        if (window.google) {
          clearInterval(t);
          init();
        }
      }, 100);
      return () => {
        cancelled = true;
        clearInterval(t);
      };
    }
    return () => {
      cancelled = true;
    };
  }, [onCredential]);

  if (!CLIENT_ID) {
    return (
      <div className="rounded-md border border-dashed bg-muted/40 p-3 text-center text-xs text-muted-foreground">
        Google 로그인 설정 필요: <code>VITE_GOOGLE_CLIENT_ID</code> 환경변수를 지정하세요.
      </div>
    );
  }

  return (
    <div className="flex min-h-[44px] justify-center">
      <div ref={ref} />
      {!ready && <span className="text-xs text-muted-foreground">로그인 버튼 불러오는 중…</span>}
    </div>
  );
}
