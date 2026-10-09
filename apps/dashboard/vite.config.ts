import process from "node:process";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

const api = process.env.HRIS_API_PROXY ?? "http://127.0.0.1:8080";

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": { target: api, changeOrigin: false },
      "/oauth2": { target: api, changeOrigin: false },
      "/login/oauth2": { target: api, changeOrigin: false },
    },
  },
  build: { sourcemap: false },
  test: {
    environment: "jsdom",
    clearMocks: true,
    restoreMocks: true,
    include: ["src/**/*.test.{ts,tsx}"],
  },
});
