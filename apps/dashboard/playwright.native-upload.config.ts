import { defineConfig } from "@playwright/test";
import base from "./playwright.native-integration.config";

/** Manual native upload/review driver; private Garage is owned by the backend fixture. */
export default defineConfig({
  ...base,
  testMatch: "document-upload.spec.ts",
  timeout: 1_200_000,
  webServer: [
    {
      command: "python3 ../../tool/dashboard_backend_fixture.py",
      env: { HRIS_TEST_WITH_WORKER: "1", HRIS_TEST_WITH_STORAGE: "1" },
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
