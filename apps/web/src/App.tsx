import { Routes, Route } from "react-router-dom";
import { AppShell } from "@/components/app-shell";
import { RequireAuth } from "@/auth/require-auth";
import { useSession } from "@/auth/use-session";
import { DashboardPage } from "@/pages/dashboard-page";
import { GeneratePage } from "@/pages/generate-page";
import { LibraryPage } from "@/pages/library-page";
import { SeriesPage } from "@/pages/series-page";
import { SchedulePage } from "@/pages/schedule-page";
import { LoginPage } from "@/pages/login-page";
import { PlaceholderPage } from "@/pages/placeholder-page";
import { ThreadsSettingsPage } from "@/pages/threads-settings-page";

export default function App() {
  useSession();
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/content/generate" element={<GeneratePage />} />
          <Route path="/content/series" element={<SeriesPage />} />
          <Route path="/content/library" element={<LibraryPage />} />
          <Route path="/schedule" element={<SchedulePage />} />
          <Route
            path="/analytics"
            element={<PlaceholderPage title="분석" description="조회·좋아요·댓글·참여율 분석." />}
          />
          <Route
            path="/settings/account"
            element={<PlaceholderPage title="계정" description="프로필 및 플랜 설정." />}
          />
          <Route path="/settings/threads" element={<ThreadsSettingsPage />} />
        </Route>
      </Route>
    </Routes>
  );
}
