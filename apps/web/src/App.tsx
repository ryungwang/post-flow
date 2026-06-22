import { Routes, Route } from "react-router-dom";
import { AppShell } from "@/components/app-shell";
import { DashboardPage } from "@/pages/dashboard-page";
import { LoginPage } from "@/pages/login-page";
import { PlaceholderPage } from "@/pages/placeholder-page";

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<AppShell />}>
        <Route path="/" element={<DashboardPage />} />
        <Route
          path="/content/generate"
          element={<PlaceholderPage title="AI 생성" description="주제만 입력하면 AI가 게시물을 만들어 줍니다." />}
        />
        <Route
          path="/content/series"
          element={<PlaceholderPage title="시리즈 생성" description="7·14·30일 콘텐츠 플랜을 자동 생성합니다." />}
        />
        <Route
          path="/content/library"
          element={<PlaceholderPage title="라이브러리" description="생성·저장된 콘텐츠 모음." />}
        />
        <Route
          path="/schedule"
          element={<PlaceholderPage title="스케줄" description="예약·자동 발행 관리." />}
        />
        <Route
          path="/analytics"
          element={<PlaceholderPage title="분석" description="조회·좋아요·댓글·참여율 분석." />}
        />
        <Route
          path="/settings/account"
          element={<PlaceholderPage title="계정" description="프로필 및 플랜 설정." />}
        />
        <Route
          path="/settings/threads"
          element={<PlaceholderPage title="Threads 연결" description="Threads 계정 연결 및 상태." />}
        />
      </Route>
    </Routes>
  );
}
