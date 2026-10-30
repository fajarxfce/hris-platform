import { defineConfig } from "@playwright/test";
import base from "./playwright.native-download.config";

/** Bounded manual emulator driver with owned API/PostgreSQL/worker/private Garage. */
export default defineConfig({
  ...base,
  testMatch: "document-metadata.spec.ts",
  timeout: 1_800_000,
  outputDir: "../../.work/metadata-native-results",
  webServer: [
    {
      command: "python3 ../../tool/dashboard_backend_fixture.py",
      env: {
        HRIS_TEST_WITH_WORKER: "1",
        HRIS_TEST_WITH_STORAGE: "1",
        HRIS_TEST_FIXTURE_LIFETIME_SECONDS: "1800",
      },
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
