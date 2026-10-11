import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./integration-worker",
  outputDir: "./worker-integration-test-results",
  workers: 1,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  retries: 0,
  reporter: [["list"], ["html", { open: "never", outputFolder: "worker-integration-report" }]],
  use: {
    ...devices["Desktop Chrome"],
    baseURL: "http://127.0.0.1:4175",
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
  },
  webServer: [
    {
      command: "python3 ../../tool/dashboard_backend_fixture.py",
      env: { HRIS_TEST_WITH_WORKER: "1" },
      wait: { stdout: /Browser API fixture ready;/u },
      reuseExistingServer: false,
      timeout: 120_000,
      gracefulShutdown: { signal: "SIGTERM", timeout: 60_000 },
    },
    {
      command: "npm run dev -- --port 4175 --strictPort",
      env: { HRIS_API_PROXY: "http://127.0.0.1:18081" },
      url: "http://127.0.0.1:4175",
      reuseExistingServer: false,
      timeout: 30_000,
    },
  ],
});
