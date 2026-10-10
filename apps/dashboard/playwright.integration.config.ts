import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./integration",
  outputDir: "./integration-test-results",
  workers: 1,
  timeout: 90_000,
  expect: { timeout: 10_000 },
  retries: 0,
  reporter: [["list"], ["html", { open: "never", outputFolder: "integration-report" }]],
  use: {
    ...devices["Desktop Chrome"],
    baseURL: "http://127.0.0.1:4174",
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
  },
  webServer: [
    {
      command: "python3 ../../tool/dashboard_backend_fixture.py",
      wait: { stdout: /Browser API fixture ready;/u },
      reuseExistingServer: false,
      timeout: 120_000,
      gracefulShutdown: { signal: "SIGTERM", timeout: 30_000 },
    },
    {
      command: "npm run dev -- --port 4174 --strictPort",
      env: { HRIS_API_PROXY: "http://127.0.0.1:18080" },
      url: "http://127.0.0.1:4174",
      reuseExistingServer: false,
      timeout: 30_000,
    },
  ],
});
