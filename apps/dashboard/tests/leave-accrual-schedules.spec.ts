import { expect, type Page, test } from "@playwright/test";
import {
  accrualScheduleId,
  accrualWire,
} from "../src/features/leave/di/leave-accrual-schedule-fixture";
import { batchReviewWire } from "../src/features/leave/di/leave-batch-fixture";
import { companyIds } from "./identity-api";
import { installLeaveAccrualScheduleApi } from "./leave-accrual-schedule-api";
import { batchPermissions } from "./leave-batches-api";

const path = "/leave/accrual-schedules",
  query = `company=${companyIds[0]}`;
const review = (page: Page) => page.getByRole("region", { name: "Review schedule", exact: true });
const periods = (page: Page) => page.getByRole("table", { name: "Periods", exact: true });
const schedules = (page: Page) =>
  page.getByRole("table", { name: "Accrual schedules", exact: true });
const departure = (page: Page) =>
  page.getByRole("dialog", { name: "Leave this page?", exact: true });
async function prepare(page: Page) {
  await page.goto(`${path}/new?${query}`);
  await page.getByRole("button", { name: "Choose leave type", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Choose leave type", exact: true })
    .getByRole("button", { name: "Select: Annual leave", exact: true })
    .click();
  await page.getByRole("button", { name: "Review schedule", exact: true }).click();
  await expect(review(page)).toContainText("Annual leave");
}
async function verify(page: Page) {
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const dialog = page.getByRole("dialog", { name: "Account verification", exact: true });
  await dialog.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await dialog.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(dialog).toHaveCount(0);
}
test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(new Date("2026-10-15T03:00:00Z"));
});
test("reviewed schedule creates fixed periods and opens current worker outcomes", async ({
  page,
}) => {
  const api = await installLeaveAccrualScheduleApi(page);
  await page.goto(`/leave/batches?${query}`);
  await page.getByRole("link", { name: "Accrual schedules", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("No accrual schedules.");
  await page.getByRole("link", { name: "Schedule accrual", exact: true }).click();
  await expect(page.getByLabel("First period", { exact: true })).toHaveValue("2026-10");
  await expect(page.getByLabel("Periods", { exact: true })).toHaveValue("3");
  expect(api.batches.reads.filter((url) => url.pathname.endsWith("/types"))).toHaveLength(0);
  await page.getByRole("button", { name: "Choose leave type", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Choose leave type", exact: true })
    .getByRole("button", { name: "Select: Annual leave", exact: true })
    .click();
  await page.getByRole("button", { name: "Review schedule", exact: true }).click();
  await expect(periods(page).getByRole("row")).toHaveCount(4);
  await expect(periods(page)).toContainText("October 2026");
  await expect(periods(page)).toContainText("Dec 1, 2026");
  await expect(periods(page)).toContainText("Minimum service (months)");
  await page.getByRole("button", { name: "Schedule", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.writes).toHaveLength(0);
  await page.getByLabel("Reason", { exact: true }).fill("Quarter-end entitlement reviewed");
  await page.screenshot({ path: "/tmp/hris-leave-accrual/review-desktop.png", fullPage: true });
  await page.getByRole("button", { name: "Schedule", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Accrual schedule created.");
  expect(api.writes[0]?.body).toMatchObject({
    firstPeriod: "2026-10",
    periods: 3,
    expectedPolicyVersion: 4,
    employeeIds: batchReviewWire.employeeIds,
  });
  expect(api.committed).toBe(1);
  await page.getByRole("link", { name: "View schedule", exact: true }).click();
  await expect(periods(page).getByRole("row")).toHaveCount(4);
  await expect(periods(page)).toContainText("Queued");
  await page.screenshot({ path: "/tmp/hris-leave-accrual/periods-desktop.png", fullPage: true });
  await periods(page)
    .getByRole("button", { name: "View progress: October 2026", exact: true })
    .click();
  await expect(page.getByRole("region", { name: "Process details", exact: true })).toContainText(
    "0 / 3",
  );
  const id = api.writes[0]?.body.id;
  await expect(page.getByRole("link", { name: "Back to schedule", exact: true })).toHaveAttribute(
    "href",
    `${path}/${id}?${query}`,
  );
  await page.getByRole("link", { name: "Back to schedule", exact: true }).click();
  await expect(periods(page)).toBeVisible();
  expect(api.identity.unhandled).toEqual([]);
});
test("annual reviews retain the selected eligibility month across years", async ({ page }) => {
  const api = await installLeaveAccrualScheduleApi(page),
    type = api.batches.types[0];
  if (!type?.accrual) throw Error("Missing policy");
  type.accrual.frequency = "ANNUAL";
  await prepare(page);
  await expect(review(page)).toContainText("Annual");
  await expect(periods(page)).toContainText("October 2027");
  await expect(periods(page)).toContainText("October 2028");
  expect(api.reads).toHaveLength(1);
  expect(api.writes).toHaveLength(0);
});
test("uncertain admission retains its original operation through MFA and later conflict", async ({
  page,
}) => {
  const api = await installLeaveAccrualScheduleApi(page);
  await prepare(page);
  await page.getByLabel("Reason", { exact: true }).fill("Original reviewed schedule");
  api.loseCommand();
  await page.getByRole("button", { name: "Schedule", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result is unconfirmed.");
  api.identity.expireMfa();
  api.rejectCommand("mfa_required", 403);
  await page.getByRole("button", { name: "Retry original command", exact: true }).click();
  await verify(page);
  expect(api.writes).toHaveLength(2);
  await expect(review(page)).toHaveCount(0);
  api.rejectCommand("stale_policy_version");
  await page.getByRole("button", { name: "Retry original command", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("button", { name: "Change selection", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Retry original command", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Accrual schedule created.");
  expect(api.writes).toHaveLength(4);
  expect(api.committed).toBe(1);
  for (const write of api.writes) expect(write).toEqual(api.writes[0]);
  await expect(page.getByRole("main")).not.toContainText("PRIVATE");
});
for (const at of ["review", "command"] as const) {
  test(`MFA during ${at} reacquires review without silently submitting`, async ({ page }) => {
    const api = await installLeaveAccrualScheduleApi(page);
    await prepare(page);
    api.identity.expireMfa();
    if (at === "review") {
      await page.getByRole("button", { name: "Change selection", exact: true }).click();
      api.rejectRead("review", "mfa_required");
      await page.getByRole("button", { name: "Review schedule", exact: true }).click();
    } else {
      await page.getByLabel("Reason", { exact: true }).fill("Private reviewed reason");
      api.rejectCommand("mfa_required", 403);
      await page.getByRole("button", { name: "Schedule", exact: true }).click();
    }
    await expect(review(page)).toHaveCount(0);
    api.rejectRead("review", null);
    await verify(page);
    await expect(review(page)).toContainText("Annual leave");
    await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
    expect(api.writes).toHaveLength(at === "command" ? 1 : 0);
    expect(api.committed).toBe(0);
  });
}
test("policy conflicts require another explicit review with the new head", async ({ page }) => {
  const api = await installLeaveAccrualScheduleApi(page);
  await prepare(page);
  const type = api.batches.types[0];
  if (!type) throw Error("Missing policy");
  type.version++;
  await page.getByLabel("Reason", { exact: true }).fill("First review");
  await page.getByRole("button", { name: "Schedule", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("policy changed");
  await expect(page.getByRole("button", { name: "Schedule", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Change selection", exact: true }).click();
  await page.getByRole("button", { name: "Review schedule", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Second review");
  await page.getByRole("button", { name: "Schedule", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Accrual schedule created.");
  expect(api.writes).toHaveLength(2);
  expect(api.writes[1]?.body.expectedPolicyVersion).toBe(5);
  expect(api.writes[1]?.operation).not.toBe(api.writes[0]?.operation);
  expect(api.committed).toBe(1);
});
test("company replacement discards a late committed result and its private review", async ({
  page,
}) => {
  const api = await installLeaveAccrualScheduleApi(page);
  await prepare(page);
  await page.getByLabel("Reason", { exact: true }).fill("North confidential review");
  const held = api.hold("write");
  try {
    await page.getByRole("button", { name: "Schedule", exact: true }).click();
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("No accrual schedules.");
    held.release();
    await expect.poll(() => api.committed).toBe(1);
    await expect(page.getByRole("main")).not.toContainText("North confidential review");
    await expect(page.getByRole("main")).not.toContainText("Accrual schedule created.");
    expect(api.writes).toHaveLength(1);
    expect(api.writes[0]?.company).toBe(companyIds[0]);
  } finally {
    held.release();
  }
});
test("denied read clears details, renewed assurance reloads, and late responses cannot cross companies", async ({
  page,
}) => {
  const api = await installLeaveAccrualScheduleApi(page);
  api.seed();
  await page.goto(`${path}/${accrualScheduleId}?${query}`);
  await expect(periods(page)).toBeVisible();
  api.identity.expireMfa();
  api.rejectRead("detail", "mfa_required");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(periods(page)).toHaveCount(0);
  api.rejectRead("detail", null);
  await verify(page);
  await expect(periods(page)).toBeVisible();
  const held = api.hold("detail");
  try {
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("status")).toContainText("No accrual schedules.");
    const delivered = api.delivered;
    held.release();
    await expect.poll(() => api.delivered).toBe(delivered + 1);
    await expect(periods(page)).toHaveCount(0);
    await expect(page.getByRole("main")).not.toContainText("Reviewed entitlement schedule");
  } finally {
    held.release();
  }
});
test("bounded history retains its cursor through period recovery", async ({ page }) => {
  const api = await installLeaveAccrualScheduleApi(page);
  for (let i = 1; i <= 23; i++) {
    const item = structuredClone(accrualWire);
    item.schedule.id = `a0000000-abcd-4000-8000-${String(i).padStart(12, "0")}`;
    if (i === 23) api.seed(companyIds[0], item);
    else api.records.get(companyIds[0])?.push(item);
  }
  const batch = api.batches.records.get(companyIds[0])?.[0];
  if (!batch) throw Error("Missing period");
  batch.batch.status = "STOPPED";
  batch.job.status = "CANCELLED";
  batch.job.version = 1;
  batch.availableActions = ["RESUME"];
  await page.goto(`${path}?${query}`);
  await expect(schedules(page).getByRole("row")).toHaveCount(21);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(schedules(page).getByRole("row")).toHaveCount(4);
  const last = schedules(page).getByRole("row").last();
  await last.getByRole("button").click();
  await periods(page)
    .getByRole("button", { name: "View progress: October 2026", exact: true })
    .click();
  await page.getByRole("link", { name: "Resume process", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Resume future period after review");
  await page.getByRole("button", { name: "Resume process", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Process resumed.");
  await page.getByRole("link", { name: "View process", exact: true }).click();
  await page.getByRole("link", { name: "Back to schedule", exact: true }).click();
  await expect(periods(page)).toContainText("Queued");
  await page.getByRole("link", { name: "Back to schedules", exact: true }).click();
  await expect(schedules(page).getByRole("row")).toHaveCount(4);
  await expect(page).toHaveURL(/after=a0000000-abcd-4000-8000-000000000020$/u);
  expect(api.identity.unhandled).toEqual([]);
});
for (const missing of ["leave.read", "leave.accrual.post"]) {
  test(`creation requires ${missing} before acquiring data`, async ({ page }) => {
    const api = await installLeaveAccrualScheduleApi(
      page,
      batchPermissions.filter((value) => value !== missing),
    );
    await page.goto(`${path}/new?${query}`);
    await expect(page.getByRole("alert")).toBeVisible();
    expect(api.reads).toHaveLength(0);
    expect(api.batches.reads).toHaveLength(0);
    expect(api.writes).toHaveLength(0);
    await expect(page.getByRole("button", { name: "Choose leave type", exact: true })).toHaveCount(
      0,
    );
  });
}
test("read-only access opens schedule history without offering admission", async ({ page }) => {
  const api = await installLeaveAccrualScheduleApi(page, ["leave.read"]);
  api.seed();
  await page.goto(`${path}?${query}`);
  await expect(schedules(page)).toContainText("Annual leave");
  await expect(page.getByRole("link", { name: "Schedule accrual", exact: true })).toHaveCount(0);
  expect(api.writes).toHaveLength(0);
});
test("Indonesian narrow dark review retains its draft and contains wide tables at 200 percent text", async ({
  page,
}) => {
  const api = await installLeaveAccrualScheduleApi(page);
  await prepare(page);
  await page.getByLabel("Reason", { exact: true }).fill("Jadwal hak cuti ditinjau");
  const reads = api.reads.length;
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue("Jadwal hak cuti ditinjau");
  expect(api.reads).toHaveLength(reads);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: "/tmp/hris-leave-accrual/review-mobile-id.png", fullPage: true });
  const originalSize = await page
    .getByLabel("Alasan", { exact: true })
    .evaluate((element) => Number.parseFloat(getComputedStyle(element).fontSize));
  await page.evaluate(() => {
    // Fluent uses pixel typography tokens; changing root rem size alone does not enlarge it.
    const sizes = [...document.querySelectorAll("main, main *")]
      .filter((element): element is HTMLElement => element instanceof HTMLElement)
      .map((element) => ({
        element,
        size: Number.parseFloat(getComputedStyle(element).fontSize),
        line: Number.parseFloat(getComputedStyle(element).lineHeight),
      }));
    for (const { element, size, line } of sizes) {
      element.style.fontSize = `${size * 2}px`;
      if (Number.isFinite(line)) element.style.lineHeight = `${line * 2}px`;
    }
    window.scrollTo(0, 0);
  });
  expect(
    await page
      .getByLabel("Alasan", { exact: true })
      .evaluate((element) => Number.parseFloat(getComputedStyle(element).fontSize)),
  ).toBe(originalSize * 2);
  const layout = await page.evaluate(() => ({
    viewport: innerWidth,
    width: document.documentElement.scrollWidth,
    overflow: [...document.querySelectorAll("main *")]
      .filter(
        (element) =>
          !element.closest(".app-table-scroll") &&
          element.getBoundingClientRect().right > innerWidth,
      )
      .map((element) => ({
        tag: element.tagName,
        className: element.className,
        right: element.getBoundingClientRect().right,
        width: element.getBoundingClientRect().width,
      })),
  }));
  await page.screenshot({
    path: "/tmp/hris-leave-accrual/review-mobile-id-200.png",
    fullPage: true,
  });
  expect(layout.width, JSON.stringify(layout.overflow)).toBeLessThanOrEqual(layout.viewport);
});
