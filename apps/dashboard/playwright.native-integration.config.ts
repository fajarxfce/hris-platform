import { defineConfig } from "@playwright/test";

/** Manual native driver joins this bounded, fictional server/browser fixture. */
export default defineConfig({
  testDir: "./integration-native",
  testMatch: "inbox-recovery.spec.ts",
  timeout: 540_000,
  workers: 1,
  retries: 0,
  outputDir: "../../.work/native-integration-results",
  reporter: [["list"]],
  use: {
    baseURL: "http://127.0.0.1:4175",
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
  },
  webServer: [
    {
      command: "python3 ../../tool/dashboard_backend_fixture.py",
      env: { HRIS_TEST_WITH_WORKER: "1" },
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
