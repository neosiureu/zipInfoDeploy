// vite.config.js
import { defineConfig } from "vite";

export default defineConfig({
  server: {
    proxy: {
      "/admin": {
        target: "http://localhost:8080",
        changeOrigin: true,
        secure: false,
      },
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
        secure: false,
      },
    },
  },
});
