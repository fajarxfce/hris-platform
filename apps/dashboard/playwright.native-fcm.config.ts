import { defineConfig } from "@playwright/test";
import base from "./playwright.native-integration.config";

export default defineConfig({
  ...base,
  testMatch: "fcm-delivery.spec.ts",
  timeout: 900_000,
  webServer: [
    {
      command: "python3 ../../tool/dashboard_backend_fixture.py",
      env: { HRIS_TEST_WITH_WORKER: "1", HRIS_TEST_WITH_FCM: "1" },
      wait: { stdout: /Browser API fixture ready;/u },
      timeout: 120_000,
      reuseExistingServer: false,
      gracefulShutdown: { signal: "SIGTERM", timeout: 60_000 },
    },
    {
      command: "npm run build && npm run preview -- --port 4175 --strictPort",
      env: { HRIS_API_PROXY: "http://127.0.0.1:18081" },
      url: "http://127.0.0.1:4175",
      timeout: 120_000,
      reuseExistingServer: false,
    },
  ],
});
