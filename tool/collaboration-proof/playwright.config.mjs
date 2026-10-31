import { defineConfig } from "@playwright/test";

const port = Number(process.env.HRIS_COLLAB_PROOF_WEB_PORT);
if (!Number.isInteger(port) || port < 1024 || port > 65535) {
  throw new Error("An owned proof web port is required");
}

export default defineConfig({
  testDir: "./tests",
  workers: 1,
  fullyParallel: false,
  retries: 0,
  timeout: 150_000,
  expect: { timeout: 30_000 },
  reporter: [["list"], ["json", { outputFile: process.env.HRIS_COLLAB_PROOF_REPORT }]],
  use: {
    baseURL: `http://127.0.0.1:${port}`,
    trace: "off",
    screenshot: "off",
    launchOptions: { args: ["--autoplay-policy=no-user-gesture-required"] },
  },
  webServer: {
    command: `npm run dev -- --port ${port} --strictPort`,
    url: `http://127.0.0.1:${port}`,
    reuseExistingServer: false,
    timeout: 60_000,
    stdout: "ignore",
    stderr: "pipe",
  },
});
