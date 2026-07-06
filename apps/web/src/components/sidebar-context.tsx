import { createContext, useContext, useState } from "react";

/** 모바일 사이드바 드로어 상태(office 패턴). 데스크톱은 항상 표시라 상태 불필요. */
interface SidebarCtx {
  mobileOpen: boolean;
  setMobileOpen: (v: boolean) => void;
}
const Ctx = createContext<SidebarCtx>({ mobileOpen: false, setMobileOpen: () => {} });

export function useSidebar() {
  return useContext(Ctx);
}

export function SidebarProvider({ children }: { children: React.ReactNode }) {
  const [mobileOpen, setMobileOpen] = useState(false);
  return <Ctx.Provider value={{ mobileOpen, setMobileOpen }}>{children}</Ctx.Provider>;
}
