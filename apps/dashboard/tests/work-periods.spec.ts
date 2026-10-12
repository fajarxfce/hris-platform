import { expect, type Page, test } from "@playwright/test";
import { identifier, periodMonth } from "../src/features/workforce/di/work-period-fixture";
import { companyIds } from "./identity-api";
import { installWorkPeriodApi } from "./work-period-api";

test.use({ timezoneId: "Pacific/Honolulu" });
test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(new Date("2026-10-13T12:00:00Z"));
});
const path = `/workforce/periods/${periodMonth}?company=${companyIds[0]}`;
const closePath = `/workforce/periods/${periodMonth}/close?company=${companyIds[0]}`;
const resultPath = `/workforce/periods/${periodMonth}/employees/${identifier(21_001)}?company=${companyIds[0]}`;
async function review(page: Page) {
  await page.goto(closePath);
  await expect(page.getByRole("region", { name: "Period overview", exact: true })).toContainText(
    "September 2026",
  );
  await page.getByLabel("Reason", { exact: true }).fill("Monthly attendance review completed");
}

test("month navigation uses company time and current months cannot be closed", async ({ page }) => {
  const api = await installWorkPeriodApi(page);
  await page.clock.setFixedTime(new Date("2026-10-01T00:30:00Z"));
  await page.goto(`/workforce/periods?company=${companyIds[0]}`);
  await expect(page).toHaveURL(path);
  await expect(page.getByRole("button", { name: "Close month", exact: true })).toBeVisible();
  await page.getByLabel("Month", { exact: true }).fill("2026-10");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page.getByRole("region", { name: "Period overview", exact: true })).toContainText(
    "This month has not ended.",
  );
  await expect(page.getByRole("button", { name: "Close month", exact: true })).toHaveCount(0);
  await page.goto(`/workforce/periods/invalid?company=${companyIds[0]}`);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.reads.map((read) => read.url.pathname.split("/").at(-1))).toEqual([
    "2026-09",
    "2026-10",
  ]);
  expect(api.identity.unhandled).toEqual([]);
});

test("published results support explicit search, bounded pages and factual attendance metrics", async ({
  page,
}) => {
  const api = await installWorkPeriodApi(page, "CLOSED");
  await page.goto(path);
  const table = page.getByRole("table", { name: "Published employee results", exact: true });
  await expect(table.getByRole("row")).toHaveCount(51);
  await page.screenshot({
    path: "../../.work/dashboard-work-period-overview-light.png",
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table).toContainText("North Employee 51");
  await expect(page.getByRole("button", { name: "Next page", exact: true })).toBeDisabled();
  const reads = api.reads.length;
  await page.getByLabel("Search by name or employee number", { exact: true }).fill("NORTH-001");
  expect(api.reads).toHaveLength(reads);
  await page.getByRole("button", { name: "Search", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(2);
  await table
    .getByRole("button", { name: "View result: North Employee 1 (NORTH-001)", exact: true })
    .click();
  await expect(page).toHaveURL(resultPath);
  await expect(page.getByRole("heading", { name: "North Employee 1", exact: true })).toBeVisible();
  await expect(
    page.getByRole("region", { name: "Accepted attendance (minutes)", exact: true }),
  ).toContainText("450");
  await expect(
    page.getByRole("region", { name: "Approved overtime (minutes)", exact: true }),
  ).toContainText("135");
  await expect(
    page.getByRole("region", { name: "Recorded absence (days)", exact: true }),
  ).toContainText("1");
  await expect(page.getByRole("region", { name: "Unrecorded days", exact: true })).toContainText(
    "26",
  );
  await expect(
    page.getByRole("table", { name: "Daily results", exact: true }).getByRole("row"),
  ).toHaveCount(31);
  await expect(page.getByRole("main")).toContainText("They are not classified as absence.");
  await page.screenshot({
    path: "../../.work/dashboard-work-period-result-light.png",
    animations: "disabled",
  });
  await page.getByRole("link", { name: "Back to period", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(51);
  expect(api.reads.some((read) => read.url.searchParams.get("after") === "NORTH-050")).toBe(true);
  expect(api.identity.unhandled).toEqual([]);
});

test("readers cannot acquire a closing form or submit a closing command", async ({ page }) => {
  const api = await installWorkPeriodApi(page, "OPEN", ["workforce.read"]);
  await page.goto(path);
  await expect(
    page.getByRole("heading", { name: "Attendance closing", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "Close month", exact: true })).toHaveCount(0);
  const reads = api.reads.length;
  await page.goto(closePath);
  await expect(page.getByRole("alert")).toContainText("You do not have access");
  expect(api.reads).toHaveLength(reads);
  expect(api.writes).toEqual([]);
});

test("closing-only operators see progress without fetching published employees", async ({
  page,
}) => {
  const api = await installWorkPeriodApi(page, "CLOSED", ["workforce.close"]);
  await page.goto(path);
  await expect(page.getByRole("region", { name: "Period overview", exact: true })).toContainText(
    "Completed",
  );
  await expect(page.getByRole("table")).toHaveCount(0);
  await expect(page.getByLabel("Search by name or employee number", { exact: true })).toHaveCount(
    0,
  );
  expect(api.reads.every((read) => read.kind === "overview")).toBe(true);
});

test("unknown closing outcomes retain their command through MFA and later version conflicts", async ({
  page,
}) => {
  const api = await installWorkPeriodApi(page);
  await review(page);
  api.loseNext();
  await page.getByRole("button", { name: "Close month", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result could not be confirmed");
  await expect(page.getByLabel("Reason", { exact: true })).toHaveAttribute("readonly", "");
  api.identity.expireMfa();
  api.rejectWrite("mfa_required", 403);
  await page.getByRole("button", { name: "Retry this request", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verify = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verify.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verify.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verify).toHaveCount(0);
  api.rejectWrite("stale_version");
  await page.getByRole("button", { name: "Retry this request", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("This record has changed");
  await expect(page.getByRole("region", { name: "Period overview", exact: true })).toHaveCount(0);
  await expect(page.getByLabel("Reason", { exact: true })).toHaveCount(0);
  const held = api.holdWrite();
  try {
    await page.getByRole("button", { name: "Retry this request", exact: true }).click();
    await held.started;
    await expect(page.getByRole("main")).not.toContainText("September 2026");
    held.release();
    await expect(page.getByRole("status")).toContainText("Closing started.");
  } finally {
    held.release();
  }
  expect(api.commits).toBe(1);
  expect(api.writes).toHaveLength(4);
  for (const write of api.writes) expect(write).toEqual(api.writes[0]);
  await page.getByRole("link", { name: "View period", exact: true }).click();
  await expect(
    page.getByRole("progressbar", { name: "Closing progress", exact: true }),
  ).toBeVisible();
  expect(api.identity.unhandled).toEqual([]);
});

test("a stopped attempt is recovered for review without starting another job", async ({ page }) => {
  const api = await installWorkPeriodApi(page, "PROCESSING");
  api.stopAttempt();
  await page.goto(path);
  await page.getByRole("button", { name: "Recover period", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Review stopped attempt");
  api.loseNext();
  await page.getByRole("button", { name: "Recover period", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result could not be confirmed");
  await page.getByRole("button", { name: "Retry this request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Period ready for review.");
  await page.getByRole("link", { name: "View period", exact: true }).click();
  await expect(page.getByRole("button", { name: "Close month", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Recover period", exact: true })).toHaveCount(0);
  expect(api.writes).toHaveLength(2);
  expect(api.writes[0]).toEqual(api.writes[1]);
  expect(api.writes[0]?.intent).toBe("recover");
  expect(api.commits).toBe(1);
});

test("a definite conflict requires protected review and a new explicit command", async ({
  page,
}) => {
  const api = await installWorkPeriodApi(page);
  await review(page);
  api.setStatus("REVIEW_REQUIRED");
  await page.getByRole("button", { name: "Close month", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("This record has changed");
  await expect(page.getByRole("button", { name: "Close month", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Review current period", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Stay on this page", exact: true })
    .click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue(
    "Monthly attendance review completed",
  );
  await page.getByRole("button", { name: "Review current period", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  await page.getByRole("button", { name: "Close month", exact: true }).click();
  await expect(page.getByText("Enter a reason.", { exact: true })).toBeVisible();
  expect(api.writes).toHaveLength(1);
  await page.getByLabel("Reason", { exact: true }).fill("Attendance corrections reviewed");
  await page.getByRole("button", { name: "Close month", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Closing started.");
  expect(api.writes[1]?.operation).not.toBe(api.writes[0]?.operation);
  expect(api.writes[1]?.body.expectedVersion).toBe(2);
});

test("company replacement disposes a pending employee result", async ({ page }) => {
  const api = await installWorkPeriodApi(page, "CLOSED"),
    held = api.holdRead("snapshot");
  try {
    await page.goto(resultPath);
    await held.started;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(
      page.getByRole("heading", { name: "Attendance closing", exact: true }),
    ).toBeVisible();
    held.release();
    await expect(
      page.getByRole("table", { name: "Published employee results", exact: true }),
    ).toContainText("South Employee 1");
    await expect(page.getByRole("main")).not.toContainText("North Employee");
    expect(api.reads.filter((read) => read.kind === "snapshot")).toHaveLength(1);
  } finally {
    held.release();
  }
});

test("a denied snapshot stays hidden during a pending retry and later network failure", async ({
  page,
}) => {
  const api = await installWorkPeriodApi(page, "CLOSED");
  await page.goto(resultPath);
  await expect(page.getByRole("heading", { name: "North Employee 1", exact: true })).toBeVisible();
  api.rejectRead("snapshot", "access_denied");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("You do not have access");
  await expect(page.getByRole("main")).not.toContainText("North Employee");
  api.rejectRead("snapshot", "connection_unavailable", 503);
  const held = api.holdRead("snapshot");
  try {
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await held.started;
    await expect(page.getByRole("main")).not.toContainText("North Employee");
    held.release();
    await expect(page.getByRole("alert")).toContainText("Unable to connect");
    await expect(page.getByRole("table")).toHaveCount(0);
    await expect(page.getByRole("main")).not.toContainText("PRIVATE DETAIL");
  } finally {
    held.release();
  }
});

test("progress observation switches to published results and then stops", async ({ page }) => {
  await page.clock.install({ time: new Date("2026-10-13T12:00:00Z") });
  const api = await installWorkPeriodApi(page, "PROCESSING");
  await page.goto(path);
  await expect(
    page.getByRole("progressbar", { name: "Closing progress", exact: true }),
  ).toHaveAttribute("aria-valuenow", "0.3");
  api.setStatus("CLOSED");
  await page.clock.runFor(4100);
  await expect(
    page.getByRole("table", { name: "Published employee results", exact: true }),
  ).toBeVisible();
  const reads = api.reads.length;
  await page.clock.runFor(20_000);
  expect(api.reads).toHaveLength(reads);
});

test("English light and Indonesian dark narrow forms preserve their draft and layout", async ({
  page,
}) => {
  const api = await installWorkPeriodApi(page);
  await review(page);
  await page.screenshot({
    path: "../../.work/dashboard-work-period-close-light.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("link", { name: "Back to period", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Stay on this page", exact: true })
    .click();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue(
    "Monthly attendance review completed",
  );
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({
    path: "../../.work/dashboard-work-period-close-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(api.writes).toEqual([]);
  expect(api.identity.unhandled).toEqual([]);
});
