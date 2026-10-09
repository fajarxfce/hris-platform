import process from "node:process";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

const api = process.env.HRIS_API_PROXY ?? "http://127.0.0.1:8080";
const clientBuild = process.env.HRIS_DASHBOARD_BUILD ?? "1";
if (!/^(0|[1-9][0-9]{0,8})$/u.test(clientBuild)) {
  throw new Error("HRIS_DASHBOARD_BUILD must be an integer between 0 and 999999999");
}

export default defineConfig({
  plugins: [react()],
  define: { __HRIS_DASHBOARD_BUILD__: Number(clientBuild) },
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
