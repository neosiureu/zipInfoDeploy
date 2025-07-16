import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  define: {
    global: "window",
  },
  plugins: [react()],
  server: {
    // host: "192.168.50.252",
    proxy: {
      "/api": {
        target: "${import.meta.env.VITE_API_BASE_URL}",
        changeOrigin: true,
        secure: false,
      },
      // /member로 시작하는 요청도 같은 백엔드로 포워딩
      "/member": {
        target: "${import.meta.env.VITE_API_BASE_URL}",
        changeOrigin: true,
        secure: false,
      },
      "/vworld": {
        target: "https://api.vworld.kr",
        changeOrigin: true,
        secure: true,
        // 요청 경로에서 /vworld 만 떼고 뒤는 그대로 전달
        rewrite: (path) => path.replace(/^\/vworld/, ""),
      },
      "/publicdata": {
        target: "https://api.data.go.kr",
        changeOrigin: true,
        secure: true,
        rewrite: (path) =>
          // "/publicdata/tn_pubr…"
          // → "/openapi/service/tn_pubr…"
          path.replace(/^\/publicdata/, "/openapi/service"),
      },
    },
  },
});
