import { expect, test } from "@playwright/test";
import { employeeId, eventId, installAttendanceApi } from "./attendance-api";
import { companyIds } from "./identity-api";

test.use({ timezoneId: "Pacific/Honolulu" });
test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(new Date("2026-10-13T12:00:00Z"));
});
const base = `/workforce/attendance/${employeeId}/days/2026-10-12`;
test("attendance directory and evidence use company time, localize and fit a narrow viewport", async ({
  page,
}) => {
  const api = await installAttendanceApi(page);
  await page.goto("/workforce/attendance?asOf=2026-10-12");
  await page
    .getByRole("button", { name: "View attendance: North Employee (EMP-001)", exact: true })
    .click();
  await page.getByRole("button", { name: "View day: Oct 12, 2026", exact: true }).click();
  await expect(page.getByRole("heading", { name: "North Employee", exact: true })).toBeVisible();
  await expect(page.getByText("Captured offline", { exact: true })).toBeVisible();
  await expect(page.getByText("Asia/Jakarta", { exact: true })).toBeVisible();
  await page.screenshot({ path: "../../.work/dashboard-attendance-light.png", fullPage: true });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByText("Dicatat offline", { exact: true })).toBeVisible();
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
    .toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-attendance-dark-mobile.png",
    fullPage: true,
  });
  expect(api.identity.unhandled).toHaveLength(0);
});
test("an unknown attendance decision retains its request through MFA renewal", async ({ page }) => {
  const api = await installAttendanceApi(page);
  await page.goto(`${base}/review/${eventId}`);
  await page.getByLabel("Decision", { exact: true }).selectOption("REJECT");
  await page.getByLabel("Reason", { exact: true }).fill("Duplicate capture");
  api.loseNextSave();
  await page.getByRole("button", { name: "Record decision", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save outcome is not confirmed");
  await expect(page.getByLabel("Decision", { exact: true })).toBeDisabled();
  api.identity.expireMfa();
  api.rejectNextSave("mfa_required");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const dialog = page.getByRole("dialog", { name: "Account verification", exact: true });
  await dialog.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await dialog.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Attendance updated.");
  expect(api.commits).toBe(1);
  expect(api.writes).toHaveLength(3);
  expect(api.writes.every((entry) => JSON.stringify(entry) === JSON.stringify(api.writes[0]))).toBe(
    true,
  );
  await expect(page.getByText("PRIVATE TECHNICAL ERROR", { exact: true })).toHaveCount(0);
});
test("a correction reloads a competing revision and preserves overnight company times through response loss", async ({
  page,
}) => {
  const api = await installAttendanceApi(page);
  api.resolvePending();
  await page.goto(`${base}/correction`);
  await page.getByLabel("Clock-in", { exact: true }).fill("2026-10-12T22:00");
  await page.getByLabel("Clock-out", { exact: true }).fill("2026-10-13T06:00");
  await page.getByLabel("Break (minutes)", { exact: true }).fill("30");
  await page.getByLabel("Reason", { exact: true }).fill("Verified night shift");
  api.replaceCorrection();
  await page.getByRole("button", { name: "Record correction", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("This record has changed");
  await page.getByRole("button", { name: "Reload latest", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Leave this page?", exact: true })
    .getByRole("button", { name: "Leave page", exact: true })
    .click();
  await expect(page.getByLabel("Record an absence", { exact: true })).toBeChecked();
  await page.getByLabel("Record an absence", { exact: true }).uncheck();
  await page.getByLabel("Clock-in", { exact: true }).fill("2026-10-12T22:00");
  await page.getByLabel("Clock-out", { exact: true }).fill("2026-10-13T06:00");
  await page.getByLabel("Break (minutes)", { exact: true }).fill("30");
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed night shift");
  api.loseNextSave();
  await page.getByRole("button", { name: "Record correction", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save outcome is not confirmed");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Attendance updated.");
  expect(api.writes[1]?.body).toMatchObject({
    expectedVersion: 0,
    clockIn: "2026-10-12T15:00:00Z",
    clockOut: "2026-10-12T23:00:00Z",
    breakMinutes: 30,
  });
  expect(api.writes[1]).toEqual(api.writes[2]);
  expect(api.commits).toBe(1);
});
test("read-only access cannot open attendance commands", async ({ page }) => {
  const api = await installAttendanceApi(page, { permissions: ["workforce.read"] });
  await page.goto(base);
  await expect(page.getByText("Captured offline", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Review event", exact: true })).toHaveCount(0);
  await page.goto(`${base}/review/${eventId}`);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.writes).toHaveLength(0);
});
test("a foreign-company deep link discards its selected employee", async ({ page }) => {
  await installAttendanceApi(page);
  await page.goto(`${base}?company=${companyIds[1]}`);
  await expect(page).toHaveURL(/\/workforce\/attendance\?company=/u);
  await expect(page.getByText("Captured offline", { exact: true })).toHaveCount(0);
});
