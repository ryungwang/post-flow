import { lazy, Suspense } from "react";
import { Routes, Route, Navigate, useLocation } from "react-router-dom";
import { Loader2 } from "lucide-react";
import { AppShell } from "@/components/app-shell";
import { RequireAuth } from "@/auth/require-auth";
import { useSession } from "@/auth/use-session";
import { LoginPage } from "@/pages/login-page";

// Route-level code splitting — pages load on demand to keep the initial bundle small.
const lazyPage = <T extends Record<string, React.ComponentType>>(
  loader: () => Promise<T>,
  key: keyof T,
) => lazy(() => loader().then((m) => ({ default: m[key] as React.ComponentType })));

const DashboardPage = lazyPage(() => import("@/pages/dashboard-page"), "DashboardPage");
const GeneratePage = lazyPage(() => import("@/pages/generate-page"), "GeneratePage");
const LibraryPage = lazyPage(() => import("@/pages/library-page"), "LibraryPage");
const SeriesPage = lazyPage(() => import("@/pages/series-page"), "SeriesPage");
const SchedulePage = lazyPage(() => import("@/pages/schedule-page"), "SchedulePage");
const AnalyticsPage = lazyPage(() => import("@/pages/analytics-page"), "AnalyticsPage");
const AccountPage = lazyPage(() => import("@/pages/account-page"), "AccountPage");
const LandingPage = lazyPage(() => import("@/pages/landing-page"), "LandingPage");
const AutomationPage = lazyPage(() => import("@/pages/automation-page"), "AutomationPage");
const ThreadsSettingsPage = lazyPage(() => import("@/pages/threads-settings-page"), "ThreadsSettingsPage");
const FaqPage = lazyPage(() => import("@/pages/faq-page"), "FaqPage");
const BrandsPage = lazyPage(() => import("@/pages/brands-page"), "BrandsPage");
const AccountPostsPage = lazyPage(() => import("@/pages/account-posts-page"), "AccountPostsPage");

/** /settings(잘못된 콜백 경로) → /settings/threads로 쿼리 보존 리다이렉트. */
function SettingsRedirect() {
  const { search } = useLocation();
  return <Navigate to={`/settings/threads${search}`} replace />;
}

function PageFallback() {
  return (
    <div className="flex min-h-[50vh] items-center justify-center">
      <Loader2 className="size-5 animate-spin text-muted-foreground" />
    </div>
  );
}

export default function App() {
  useSession();
  return (
    <Suspense fallback={<PageFallback />}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        {/* 빌링 "데모 체험하기" 진입점(postflow.synub.io/demo) → 데모 자동로그인 */}
        <Route path="/demo" element={<Navigate to="/login?demo" replace />} />
        <Route path="/lp/:slug" element={<LandingPage />} />
        <Route element={<RequireAuth />}>
          <Route element={<AppShell />}>
            <Route path="/" element={<DashboardPage />} />
            <Route path="/content/generate" element={<GeneratePage />} />
            <Route path="/content/series" element={<SeriesPage />} />
            <Route path="/content/library" element={<LibraryPage />} />
            <Route path="/content/threads-posts" element={<AccountPostsPage />} />
            <Route path="/schedule" element={<SchedulePage />} />
            <Route path="/automation" element={<AutomationPage />} />
            <Route path="/analytics" element={<AnalyticsPage />} />
            <Route path="/settings/account" element={<AccountPage />} />
            <Route path="/settings/threads" element={<ThreadsSettingsPage />} />
            {/* Threads OAuth 콜백이 /settings?threads=connected로 와도 실제 페이지로(쿼리 보존). */}
            <Route path="/settings" element={<SettingsRedirect />} />
            <Route path="/brands" element={<BrandsPage />} />
            <Route path="/help" element={<FaqPage />} />
          </Route>
        </Route>
      </Routes>
    </Suspense>
  );
}
