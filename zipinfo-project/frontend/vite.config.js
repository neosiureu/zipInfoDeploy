import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

// Vite config is run in Node; can't use import.meta.env directly at top-level.
// Use loadEnv() to pull .env files.
export default defineConfig(({ mode }) => {
  // Loads .env, .env.local, .env.[mode], etc.
  const env = loadEnv(mode, process.cwd(), "");

  // Where your Spring 백엔드가 떠 있음.
  // 프로덕션 .env.production 에서 https://www.zipinfo.site (또는 api용 서브도메인) 지정.
  // 개발 시 없으면 로컬 백엔드 fallback.
  const backend = env.VITE_BACKEND_ORIGIN || "http://localhost:8080";

  return {
    define: {
      global: "window",
    },
    plugins: [react()],
    server: {
      // host: "192.168.50.252", // 필요 시 해제
      proxy: {
        // REST API: /api/... → backend/...
        "/api": {
          target: backend,
          changeOrigin: true,
          secure: false,
          // 백엔드가 경로 앞에 /api 를 기대하지 않으면 떼준다.
          // (지금 Spring 컨트롤러는 /sale/... /stock/... 이므로 떼는 게 일반적.)
          rewrite: (path) => path.replace(/^\/api/, ""),
        },

        // 역사적으로 /member 로 직접 호출하는 코드가 있다면 지원
        "/member": {
          target: backend,
          changeOrigin: true,
          secure: false,
          // rewrite: (p) => p, // 그대로 전달 (기본)
        },

        // 정적 리소스들: 이미지/업로드 등
        "/images": {
          target: backend,
          changeOrigin: true,
          secure: false,
        },
        "/myPage": {
          target: backend,
          changeOrigin: true,
          secure: false,
        },
        "/message": {
          target: backend,
          changeOrigin: true,
          secure: false,
        },
        "/uploads": {
          target: backend,
          changeOrigin: true,
          secure: false,
        },

        // 외부 API
        "/vworld": {
          target: "https://api.vworld.kr",
          changeOrigin: true,
          secure: true,
          rewrite: (path) => path.replace(/^\/vworld/, ""),
        },
        "/publicdata": {
          target: "https://api.data.go.kr",
          changeOrigin: true,
          secure: true,
          rewrite: (path) => path.replace(/^\/publicdata/, "/openapi/service"),
        },
      },
    },
  };
});
