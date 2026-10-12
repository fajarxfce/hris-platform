import { expect, test } from "@playwright/test";
import { employeeId } from "./attendance-api";
import { installAttendanceExceptionsApi } from "./attendance-exceptions-api";
import { companyIds } from "./identity-api";

test.use({ timezoneId: "Pacific/Honolulu" });
test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(new Date("2026-10-13T12:00:00Z"));
});
const path = "/workforce/attendance/exceptions";
const scope = `company=${companyIds[0]}&from=2026-10-07&until=2026-10-13`;

test("company exceptions keep bounded filters and pagination and open an independently read day", async ({
  page,
}) => {
  const api = await installAttendanceExceptionsApi(page);
  await page.goto("/workforce/attendance");
  await page
    .getByRole("navigation", { name: "Navigation", exact: true })
    .getByRole("link", { name: "Attendance exceptions", exact: true })
    .click();
  await expect(page).toHaveURL(`${path}?${scope}`);
  const table = page.getByRole("table", { name: "Employee days", exact: true });
  await expect(table.getByRole("row")).toHaveCount(51);
  const menu = page.getByRole("navigation", { name: "Navigation", exact: true });
  await expect(menu.locator('[aria-current="page"]')).toHaveCount(1);
  await expect(
    menu.getByRole("link", { name: "Attendance exceptions", exact: true }),
  ).toHaveAttribute("aria-current", "page");
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(4);
  await expect(page.getByRole("button", { name: "Next page", exact: true })).toBeDisabled();
  await page
    .getByRole("combobox", { name: "Review status", exact: true })
    .selectOption("INCOMPLETE");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page).toHaveURL(`${path}?${scope}&kind=INCOMPLETE`);
  await expect(table.getByRole("row")).toHaveCount(27);
  await expect(page.getByRole("button", { name: "First page", exact: true })).toBeDisabled();
  await page.getByRole("combobox", { name: "Review status", exact: true }).selectOption("");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(51);
  await page.getByRole("button", { name: "View day: EMP-001, Oct 12, 2026", exact: true }).click();
  await expect(page).toHaveURL(`/workforce/attendance/${employeeId}/days/2026-10-12?${scope}`);
  await expect(page.getByRole("heading", { name: "North Employee", exact: true })).toBeVisible();
  await page.goBack();
  await expect(page).toHaveURL(`${path}?${scope}`);
  await expect(table.getByRole("row")).toHaveCount(51);
  expect(api.reads).toHaveLength(5);
  expect(api.reads.every((url) => url.searchParams.get("limit") === "50")).toBe(true);
  expect(api.reads[2]?.searchParams.get("after")).toBe(null);
  expect(api.identity.unhandled).toHaveLength(0);
});

test("refresh hides prior exceptions until a fresh empty result is admitted", async ({ page }) => {
  const api = await installAttendanceExceptionsApi(page, { count: 3 });
  await page.goto(path);
  const table = page.getByRole("table", { name: "Employee days", exact: true });
  await expect(table).toBeVisible();
  const held = api.holdRead();
  api.resolveAll();
  try {
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await held.started;
    await expect(table).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Refresh", exact: true })).toBeDisabled();
    held.release();
    await expect(
      page.getByText("No attendance exceptions match these filters.", { exact: true }),
    ).toBeVisible();
    await expect(page.getByRole("button", { name: "Next page", exact: true })).toBeDisabled();
    expect(api.reads).toHaveLength(2);
  } finally {
    held.release();
  }
});

test("invalid URL filters and unavailable grants never acquire an exception page", async ({
  page,
}) => {
  const api = await installAttendanceExceptionsApi(page);
  for (const query of [
    `company=${companyIds[0]}&from=2026-09-01&until=2026-10-13`,
    `${scope}&kind=ABSENT`,
    `${scope}&after=${"x".repeat(161)}`,
  ]) {
    await page.goto(`${path}?${query}`);
    await expect(page.getByRole("alert")).toBeVisible();
    await expect(page.getByRole("table", { name: "Employee days", exact: true })).toHaveCount(0);
  }
  expect(api.reads).toHaveLength(0);
  api.identity.setPermissions(["attendance.self.record"]);
  await page.goto(path);
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(
    page
      .getByRole("navigation", { name: "Navigation", exact: true })
      .getByRole("link", { name: "Attendance exceptions", exact: true }),
  ).toHaveCount(0);
  expect(api.reads).toHaveLength(0);
});

test("company replacement clears filters and cannot display a pending old-company result", async ({
  page,
}) => {
  const api = await installAttendanceExceptionsApi(page, { count: 3 }),
    held = api.holdRead();
  try {
    await page.goto(`${path}?${scope}&kind=PENDING`);
    await held.started;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page).toHaveURL(
      `${path}?company=${companyIds[1]}&from=2026-10-07&until=2026-10-13`,
    );
    await expect(
      page.getByRole("cell", { name: "South North Employee · EMP-001", exact: true }),
    ).toBeVisible();
    held.release();
    await expect(
      page.getByRole("cell", { name: "North Employee · EMP-001", exact: true }),
    ).toHaveCount(0);
    expect(api.reads).toHaveLength(2);
    expect(api.reads[1]?.searchParams.get("kind")).toBe(null);
  } finally {
    held.release();
  }
});

test("MFA renewal keeps the old queue hidden until an explicit fresh read", async ({ page }) => {
  const api = await installAttendanceExceptionsApi(page, { count: 3 });
  await page.goto(path);
  const table = page.getByRole("table", { name: "Employee days", exact: true });
  await expect(table).toBeVisible();
  api.identity.expireMfa();
  api.rejectNext("mfa_required");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const dialog = page.getByRole("dialog", { name: "Account verification", exact: true });
  await dialog.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await dialog.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(table).toHaveCount(0);
  expect(api.reads).toHaveLength(2);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(table).toBeVisible();
  await expect(page.getByText("PRIVATE TECHNICAL ERROR", { exact: true })).toHaveCount(0);
  expect(api.reads).toHaveLength(3);
});

test("company receipt times and narrow light/dark layouts localize without reloading", async ({
  page,
}) => {
  const api = await installAttendanceExceptionsApi(page, { count: 3 });
  await page.setViewportSize({ width: 430, height: 932 });
  await page.goto(path);
  const table = page.getByRole("table", { name: "Employee days", exact: true });
  await expect(table.getByRole("row")).toHaveCount(4);
  await expect(
    table.getByRole("cell", { name: "Oct 13, 2026, 10:15 AM", exact: true }),
  ).toHaveCount(3);
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
    .toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-attendance-exceptions-light.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Pemeriksaan presensi", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("combobox", { name: "Status pemeriksaan", exact: true }),
  ).toBeVisible();
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
    .toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-attendance-exceptions-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(api.reads).toHaveLength(1);
  expect(api.identity.unhandled).toHaveLength(0);
});
