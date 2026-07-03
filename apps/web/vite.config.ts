import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import path from "node:path";

// SPA는 API를 호스트 기준으로 직접 호출(office 컨벤션). dev=VITE_API_BASE_URL ?? http://localhost:8080.
// 백엔드 CORS가 localhost:5173 허용하므로 dev proxy 불필요.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    port: 5173,
  },
});
