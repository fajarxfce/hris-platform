import { expect, type Page, test } from "@playwright/test";
import type { JobDto } from "../src/features/jobs/data/models/job-dto";
import { companyIds, installIdentityApi } from "./identity-api";

const path = "/administration/jobs";
const permissions = ["jobs.read", "jobs.manage"];
const jobId = (company = 0, value = 1) =>
  `60000000-0000-4000-8000-${company + 1}${String(value).padStart(11, "0")}`;
const at = "2026-10-01T00:00:00.000123Z";
const job = (company = 0): JobDto => ({
  id: jobId(company),
  kind: "WORKFORCE_CLOSE",
  status: "QUEUED",
  completedItems: 1,
  totalItems: 3,
  progressMode: "FIXED_TOTAL",
  attempts: 0,
  cancellationRequested: false,
  failureCode: null,
  createdAt: at,
  finishedAt: null,
  version: 2,
  availableActions: ["cancel"],
  scheduledFor: null,
  availableAt: "2026-10-01T00:00:00Z",
});

async function installJobs(page: Page, paginate = false) {
  const requests: URL[] = [];
  const commands: { companyId: string; jobId: string; body: unknown; csrf: string | undefined }[] =
    [];
  const current = [job(0), job(1)];
  let cancellation: "accepted" | "lost" | "stale" | "denied" = "accepted";
  let listFailure: string | null = null;
  await page.route("**/api/v1/companies/*/jobs{,?*,/**}", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    requests.push(url);
    expect(request.headers()["x-hris-client-platform"]).toBe("WEB");
    const company = url.pathname.split("/")[4] ?? "";
    const index = company === companyIds[0] ? 0 : 1;
    const id = url.pathname.split("/")[6];
    const stored = current[index] ?? job(index);
    if (request.method() === "POST") {
      const body = request.postDataJSON() as { expectedVersion: number };
      commands.push({
        companyId: company,
        jobId: id ?? "",
        body,
        csrf: request.headers()["x-csrf-token"],
      });
      expect(request.headers()["x-csrf-token"]).toMatch(/^fixture-csrf-\d+$/u);
      if (cancellation === "denied")
        return route.fulfill({
          status: 403,
          json: { code: "company_access_denied", detail: "PRIVATE SERVER DETAIL" },
        });
      if (cancellation === "stale") {
        current[index] = { ...stored, version: stored.version + 1 };
        cancellation = "accepted";
        return route.fulfill({
          status: 409,
          json: { code: "stale_version", detail: "PRIVATE SQL DETAIL" },
        });
      }
      expect(body).toEqual({ expectedVersion: stored.version });
      current[index] = {
        ...stored,
        cancellationRequested: true,
        availableActions: [],
        version: stored.version + 1,
      };
      if (cancellation === "lost") return route.abort("connectionreset");
      return route.fulfill({ json: current[index] });
    }
    if (id) return route.fulfill({ json: { ...stored, id } });
    expect(url.searchParams.get("size")).toBe("50");
    if (listFailure)
      return route.fulfill({
        status: 403,
        json: { code: listFailure, detail: "PRIVATE LIST FAILURE" },
      });
    const count = paginate ? (url.searchParams.has("beforeAt") ? 2 : 50) : 1;
    const items = Array.from({ length: count }, (_, offset) => ({
      ...stored,
      id: count === 1 ? stored.id : jobId(index, count === 50 ? 100 - offset : 2 - offset),
      request: { private: "PRIVATE JOB INPUT" },
      checkpoint: "PRIVATE CHECKPOINT",
    }));
    return route.fulfill({
      json: {
        items,
        nextCreatedAt: count === 50 ? at : null,
        nextId: count === 50 ? jobId(index, 51) : null,
      },
    });
  });
  return {
    requests,
    commands,
    cancellation: (mode: typeof cancellation) => {
      cancellation = mode;
    },
    denyList: () => {
      listFailure = "company_access_denied";
    },
    finish: () => {
      current[0] = {
        ...(current[0] ?? job()),
        status: "CANCELLED",
        finishedAt: "2026-10-01T01:00:00Z",
        version: (current[0]?.version ?? 0) + 1,
        availableActions: [],
      };
    },
  };
}

test("jobs show localized metadata with responsive Fluent details and keyboard restoration", async ({
  page,
}) => {
  const identity = await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installJobs(page);
  await page.goto(path);
  const table = page.getByRole("table", { name: "Company jobs", exact: true });
  await expect(table.getByRole("row")).toHaveCount(2);
  await expect(table).toContainText("Attendance closing");
  await page.screenshot({
    path: "../../.work/dashboard-jobs-light.png",
    fullPage: true,
    animations: "disabled",
  });
  const details = table.getByRole("button", { name: /^View details:/u });
  const loaded = api.requests.filter((url) => url.searchParams.has("size")).length;
  await details.focus();
  await page.keyboard.press("Enter");
  const panel = page.getByRole("dialog", { name: "Job details", exact: true });
  await expect(panel).toContainText(jobId());
  await expect(panel).toContainText("1 / 3");
  await expect(panel).not.toContainText("PRIVATE");
  expect(api.requests.filter((url) => url.searchParams.has("size"))).toHaveLength(loaded);
  await page.keyboard.press("Escape");
  await expect(panel).toHaveCount(0);
  await expect(details).toBeFocused();
  await expect(table).toBeVisible();
  const refreshed = api.requests.length;
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByRole("table", { name: "Job perusahaan", exact: true })).toContainText(
    "Penutupan kehadiran",
  );
  await page.screenshot({
    path: "../../.work/dashboard-jobs-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({
    path: "../../.work/dashboard-jobs-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  expect(api.requests).toHaveLength(refreshed);
  await page.getByRole("button", { name: /^Lihat detail:/u }).click();
  await expect(page.getByRole("dialog", { name: "Detail job", exact: true })).toContainText(
    jobId(),
  );
  await page.screenshot({
    path: "../../.work/dashboard-job-detail-mobile.png",
    fullPage: false,
    animations: "disabled",
  });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  expect(identity.unhandled).toEqual([]);
});

test("job pagination preserves server microseconds and recovers invalid links without a retry loop", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installJobs(page, true);
  await page.goto(`${path}?beforeId=invalid`);
  await expect(page.getByRole("alert")).toContainText("Return to the first page.");
  expect(api.requests).toEqual([]);
  await page.getByRole("button", { name: "First page", exact: true }).click();
  const table = page.getByRole("table", { name: "Company jobs", exact: true });
  await expect(table.getByRole("row")).toHaveCount(51);
  await page.getByRole("button", { name: "Older jobs", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(3);
  expect(Object.fromEntries(api.requests.at(-1)?.searchParams ?? [])).toEqual({
    size: "50",
    beforeAt: at,
    beforeId: jobId(0, 51),
  });
  await expect(page.getByRole("button", { name: "Older jobs", exact: true })).toBeDisabled();
  await page.goBack();
  await expect(table.getByRole("row")).toHaveCount(51);
  await page.goForward();
  await expect(table.getByRole("row")).toHaveCount(3);
  await page.getByRole("button", { name: "First page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(51);
});

test("cancellation requires confirmation, preserves completed work and waits for a terminal worker status", async ({
  page,
}) => {
  const focusErrors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error" && message.text().includes("Keyborg"))
      focusErrors.push(message.text());
  });
  await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installJobs(page);
  await page.goto(`${path}?company=${companyIds[0]}&job=${jobId()}`);
  const panel = page.getByRole("dialog", { name: "Job details", exact: true });
  const request = panel.getByRole("button", { name: "Request cancellation", exact: true });
  await expect(request).toBeEnabled();
  await request.click();
  const confirmation = page.getByRole("dialog", { name: "Cancel this job?", exact: true });
  await expect(confirmation).toContainText("Completed work will be retained.");
  await page.keyboard.press("Escape");
  await expect(confirmation).toHaveCount(0);
  await expect(request).toBeFocused();
  expect(api.commands).toEqual([]);
  await request.click();
  await confirmation.getByRole("button", { name: "Request cancellation", exact: true }).click();
  await expect(confirmation).toHaveCount(0);
  await expect(panel.getByRole("status")).toHaveText("Cancellation requested");
  await expect(panel.getByRole("region", { name: "Job details", exact: true })).toContainText(
    "Queued",
  );
  expect(api.commands).toHaveLength(1);
  expect(api.commands[0]).toMatchObject({
    companyId: companyIds[0],
    jobId: jobId(),
    body: { expectedVersion: 2 },
  });
  await expect(request).toBeDisabled();
  await panel.getByRole("button", { name: "Close", exact: true }).click();
  const table = page.getByRole("table", { name: "Company jobs", exact: true });
  await expect(table).toContainText("Cancellation requested");
  await table.getByRole("button", { name: /^View details:/u }).click();
  await expect(panel.getByRole("status")).toHaveText("Cancellation requested");
  api.finish();
  await panel.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(panel.getByRole("region", { name: "Job details", exact: true })).toContainText(
    "Cancelled",
  );
  await expect(panel.getByRole("status")).toHaveCount(0);
  await expect(request).toBeDisabled();
  expect(api.commands).toHaveLength(1);
  expect(focusErrors).toEqual([]);
});

test("a lost cancellation response is reconciled by reading status without repeating the command", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installJobs(page);
  api.cancellation("lost");
  await page.goto(`${path}?job=${jobId()}`);
  const panel = page.getByRole("dialog", { name: "Job details", exact: true });
  await panel.getByRole("button", { name: "Request cancellation", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Cancel this job?", exact: true })
    .getByRole("button", { name: "Request cancellation", exact: true })
    .click();
  await expect(panel.getByRole("status")).toContainText("Cancellation could not be confirmed.");
  await expect(panel.getByRole("region", { name: "Job details", exact: true })).toHaveCount(0);
  await expect(
    panel.getByRole("button", { name: "Request cancellation", exact: true }),
  ).toBeDisabled();
  await expect(panel).not.toContainText("PRIVATE");
  expect(api.commands).toHaveLength(1);
  await panel.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(panel.getByRole("status")).toHaveText("Cancellation requested");
  expect(api.commands).toHaveLength(1);
});

test("a stale cancellation needs a fresh version and revoked access clears both details and list", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installJobs(page);
  api.cancellation("stale");
  await page.goto(`${path}?job=${jobId()}`);
  const panel = page.getByRole("dialog", { name: "Job details", exact: true });
  await panel.getByRole("button", { name: "Request cancellation", exact: true }).click();
  const confirmation = page.getByRole("dialog", { name: "Cancel this job?", exact: true });
  await confirmation.getByRole("button", { name: "Request cancellation", exact: true }).click();
  await expect(panel.getByRole("alert")).toContainText("This record has changed.");
  expect(api.commands).toHaveLength(1);
  await panel.getByRole("button", { name: "Refresh", exact: true }).click();
  api.cancellation("denied");
  await panel.getByRole("button", { name: "Request cancellation", exact: true }).click();
  await confirmation.getByRole("button", { name: "Request cancellation", exact: true }).click();
  await expect(panel.getByRole("alert")).toContainText("Company access is unavailable.");
  expect(api.commands[1]?.body).toEqual({ expectedVersion: 3 });
  await expect(panel).not.toContainText("PRIVATE");
  await expect(panel.getByRole("region", { name: "Job details", exact: true })).toHaveCount(0);
  api.denyList();
  await panel.getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Company access is unavailable.");
  await expect(page.getByRole("table")).toHaveCount(0);
});

test("closing pending details and switching company discard the old response and selection", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installJobs(page);
  let enter!: () => void;
  let release!: () => void;
  const entered = new Promise<void>((resolve) => {
    enter = resolve;
  });
  const held = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route(`**/api/v1/companies/${companyIds[0]}/jobs/${jobId()}`, async (route) => {
    enter();
    await held;
    await route.fulfill({ json: job() });
  });
  try {
    await page.goto(`${path}?company=${companyIds[0]}&job=${jobId()}`);
    await entered;
    await page
      .getByRole("dialog", { name: "Job details", exact: true })
      .getByRole("button", { name: "Close", exact: true })
      .click();
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    const table = page.getByRole("table", { name: "Company jobs", exact: true });
    await expect(table).toBeVisible();
    await expect(page.getByRole("main")).toContainText("South Company");
    release();
    await table.getByRole("button", { name: /^View details:/u }).click();
    const panel = page.getByRole("dialog", { name: "Job details", exact: true });
    await expect(panel).toContainText(jobId(1));
    await expect(panel).not.toContainText(jobId());
    expect(api.requests.at(-1)?.pathname).toBe(
      `/api/v1/companies/${companyIds[1]}/jobs/${jobId(1)}`,
    );
    await panel.getByRole("button", { name: "Close", exact: true }).click();
    await page.getByRole("button", { name: "Sign out", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Sign in", exact: true })).toBeVisible();
    await expect(page.getByRole("table")).toHaveCount(0);
  } finally {
    release();
  }
});

test("members without company-wide job grants retain their own job monitor", async ({ page }) => {
  await installIdentityApi(page, { signedIn: true, permissions: [] });
  const api = await installJobs(page);
  await page.goto(`${path}?job=invalid`);
  const panel = page.getByRole("dialog", { name: "Job details", exact: true });
  await expect(panel.getByRole("alert")).toContainText("The job was not found");
  expect(api.requests.every((url) => !url.pathname.endsWith("/invalid"))).toBe(true);
  await panel.getByRole("button", { name: "Close", exact: true }).click();
  await expect(page.getByRole("table", { name: "Your jobs", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Jobs", exact: true })).toHaveAttribute(
    "aria-current",
    "page",
  );
  api.denyList();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("table")).toHaveCount(0);
  await expect(page.getByRole("alert")).toContainText("Company access is unavailable.");
});
