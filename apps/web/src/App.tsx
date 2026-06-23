import { Routes, Route } from "react-router-dom";
import { AppShell } from "@/components/app-shell";
import { RequireAuth } from "@/auth/require-auth";
import { useSession } from "@/auth/use-session";
import { DashboardPage } from "@/pages/dashboard-page";
import { GeneratePage } from "@/pages/generate-page";
import { LibraryPage } from "@/pages/library-page";
import { SeriesPage } from "@/pages/series-page";
import { SchedulePage } from "@/pages/schedule-page";
import { AnalyticsPage } from "@/pages/analytics-page";
import { AccountPage } from "@/pages/account-page";
import { LoginPage } from "@/pages/login-page";
import { LandingPage } from "@/pages/landing-page";
import { AutomationPage } from "@/pages/automation-page";
import { ThreadsSettingsPage } from "@/pages/threads-settings-page";
import { FaqPage } from "@/pages/faq-page";
import { BrandsPage } from "@/pages/brands-page";

export default function App() {
  useSession();
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/lp/:slug" element={<LandingPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/content/generate" element={<GeneratePage />} />
          <Route path="/content/series" element={<SeriesPage />} />
          <Route path="/content/library" element={<LibraryPage />} />
          <Route path="/schedule" element={<SchedulePage />} />
          <Route path="/automation" element={<AutomationPage />} />
          <Route path="/analytics" element={<AnalyticsPage />} />
          <Route path="/settings/account" element={<AccountPage />} />
          <Route path="/settings/threads" element={<ThreadsSettingsPage />} />
          <Route path="/brands" element={<BrandsPage />} />
          <Route path="/help" element={<FaqPage />} />
        </Route>
      </Route>
    </Routes>
  );
}
