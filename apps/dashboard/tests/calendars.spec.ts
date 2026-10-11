import { expect, type Page, test } from "@playwright/test";
import { employeeId, identifier, installCalendarsApi } from "./calendars-api";
import { companyIds } from "./identity-api";

test.use({ timezoneId: "Pacific/Honolulu" });
test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(new Date("2026-10-11T12:00:00Z"));
});
const query = "from=2026-10-12&until=2026-10-18";
const detail = `/workforce/calendars/${employeeId}`;
const weekly = `${detail}/schedule?${query}`;
const departure = (page: Page) =>
  page.getByRole("dialog", { name: "Leave this page?", exact: true });

test("calendar navigation retains selected shift snapshots through catalog pages, localization and narrow layout", async ({
  page,
}) => {
  const api = await installCalendarsApi(page);
  await page.goto("/workforce/calendars?asOf=2026-10-12");
  await page
    .getByRole("button", { name: "View calendar: North Employee (EMP-001)", exact: true })
    .click();
  await expect(page.getByRole("table").first()).toContainText("Oct 12, 2026");
  await expect(page.getByRole("table").first()).toContainText("next day");
  await page.getByRole("link", { name: "Edit weekly schedule", exact: true }).click();
  await expect(page.getByLabel("Monday", { exact: true })).toHaveValue(`${identifier()}:0`);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(page.getByLabel("Tuesday", { exact: true }).locator("option")).toHaveCount(7);
  await expect(page.getByLabel("Monday", { exact: true })).toHaveValue(`${identifier()}:0`);
  await page.getByLabel("Tuesday", { exact: true }).selectOption(`${identifier(51)}:0`);
  await page.getByLabel("Search shifts", { exact: true }).fill("Shift 55");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page.getByLabel("Tuesday", { exact: true })).toHaveValue(`${identifier(51)}:0`);
  await expect(page.getByLabel("Tuesday", { exact: true }).locator("option")).toHaveCount(4);
  await page.getByLabel("Reason", { exact: true }).fill(" New coverage ");
  await page.screenshot({ path: "../../.work/dashboard-calendar-light.png", fullPage: true });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByLabel("Selasa", { exact: true })).toHaveValue(`${identifier(51)}:0`);
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
    .toBe(true);
  await page.screenshot({ path: "../../.work/dashboard-calendar-dark-mobile.png", fullPage: true });
  await page.getByRole("button", { name: "Simpan", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Kalender tersimpan.");
  expect(api.writes[0]?.body).toEqual({
    effectiveFrom: "2026-10-12",
    expectedVersion: 4,
    reason: "New coverage",
    days: { MONDAY: { id: identifier(), version: 0 }, TUESDAY: { id: identifier(51), version: 0 } },
  });
  expect(api.identity.unhandled).toHaveLength(0);
});

test("an unknown weekly save remains immutable through MFA renewal and explicit receipt replay", async ({
  page,
}) => {
  const api = await installCalendarsApi(page);
  await page.goto(weekly);
  await page.getByLabel("Tuesday", { exact: true }).selectOption(`${identifier(2)}:0`);
  await page.getByLabel("Reason", { exact: true }).fill("Extra coverage");
  api.loseNextSave();
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save outcome is not confirmed");
  await expect(page.getByLabel("Tuesday", { exact: true })).toBeDisabled();
  api.identity.expireMfa();
  api.rejectNextSave("mfa_required");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  expect(api.writes).toHaveLength(2);
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Calendar saved.");
  expect(api.commits).toBe(1);
  expect(api.writes).toHaveLength(3);
  expect(api.writes.every((write) => JSON.stringify(write) === JSON.stringify(api.writes[0]))).toBe(
    true,
  );
  await expect(page.getByText("PRIVATE TECHNICAL ERROR", { exact: true })).toHaveCount(0);
});

test("reload clears drafts even for identical server values and uses the latest global schedule version", async ({
  page,
}) => {
  const api = await installCalendarsApi(page);
  await page.goto(weekly);
  await page.getByLabel("Tuesday", { exact: true }).selectOption(`${identifier(2)}:0`);
  await page.getByRole("button", { name: "Reload latest", exact: true }).click();
  await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Tuesday", { exact: true })).toHaveValue("");
  await page.getByLabel("Reason", { exact: true }).fill("Calendar correction");
  api.replaceVersion(5);
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("This record has changed");
  await page.getByRole("button", { name: "Reload latest", exact: true }).click();
  await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed correction");
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Calendar saved.");
  expect(api.writes.map((write) => write.body.expectedVersion)).toEqual([4, 5]);
  expect(api.writes[0]?.operation).not.toBe(api.writes[1]?.operation);
});

test("a revised shift never silently replaces the original assignment snapshot", async ({
  page,
}) => {
  const api = await installCalendarsApi(page);
  api.reviseShift();
  await page.goto(weekly);
  const monday = page.getByLabel("Monday", { exact: true });
  await expect(monday).toHaveValue(`${identifier()}:0`);
  await expect(monday.locator("option:checked")).toContainText("Shift 1");
  await page.getByLabel("Reason", { exact: true }).fill("Review changed template");
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("shift has changed");
  await page.getByRole("button", { name: "Reload latest", exact: true }).click();
  await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(monday).toHaveValue(`${identifier()}:0`);
  await monday.selectOption(`${identifier()}:1`);
  await page.getByLabel("Reason", { exact: true }).fill("Use current revision");
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Calendar saved.");
  expect(api.writes[1]?.body).toMatchObject({ days: { MONDAY: { version: 1 } } });
});

test("an explicit roster day off keeps its own version through a lost response", async ({
  page,
}) => {
  const api = await installCalendarsApi(page);
  await page.goto(`${detail}/roster/2026-10-13?${query}`);
  await expect(page.getByLabel("Shift", { exact: true })).toHaveValue("");
  await page.getByLabel("Reason", { exact: true }).fill("Confirm day off");
  api.loseNextSave();
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save outcome is not confirmed");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Calendar saved.");
  expect(api.writes[0]?.body).toEqual({
    shift: null,
    expectedVersion: 6,
    reason: "Confirm day off",
  });
  expect(api.writes[1]).toEqual(api.writes[0]);
  expect(api.commits).toBe(1);
  await page.getByRole("link", { name: "View calendar", exact: true }).last().click();
  await expect(page.getByRole("table").first()).toContainText("Daily roster");
  await expect(page.getByRole("table").first()).toContainText("Shift 1");
});

test("a pending save protects company departure and cannot restore the previous company after release", async ({
  page,
}) => {
  const api = await installCalendarsApi(page);
  await page.goto(weekly);
  await page.getByLabel("Reason", { exact: true }).fill("Pending change");
  const pending = api.holdSave();
  try {
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await pending.started;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await departure(page).getByRole("button", { name: "Stay on this page", exact: true }).click();
    await expect(page.getByRole("combobox", { name: "Company", exact: true })).toHaveValue(
      companyIds[0],
    );
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
    pending.release();
    await expect(page.getByRole("table")).toContainText("South Employee");
    await expect(page.getByRole("status").filter({ hasText: "Calendar saved." })).toHaveCount(0);
    expect(api.writes).toHaveLength(1);
  } finally {
    pending.release();
  }
});

test("read-only access rejects a pinned foreign calendar and invalid ranges before transport", async ({
  page,
}) => {
  const api = await installCalendarsApi(page, { permissions: ["workforce.read"] });
  await page.goto(`${detail}?company=${companyIds[1]}&${query}`);
  await expect(page.getByRole("table")).toContainText("North Employee");
  expect(api.reads.every((url) => url.pathname.includes(companyIds[0]))).toBe(true);
  await page
    .getByRole("button", { name: "View calendar: North Employee (EMP-001)", exact: true })
    .click();
  await expect(page.getByRole("heading", { name: "North Employee", exact: true })).toBeVisible();
  await expect(page.getByRole("table", { name: "Work calendar", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Edit weekly schedule", exact: true })).toHaveCount(
    0,
  );
  await expect(page.getByRole("button", { name: /^Set daily roster:/u })).toHaveCount(0);
  const reads = api.reads.length;
  await page.getByLabel("Until", { exact: true }).fill("2027-12-31");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.reads).toHaveLength(reads);
  await page.goto(weekly);
  await expect(page.getByRole("button", { name: "Save", exact: true })).toHaveCount(0);
  expect(api.writes).toHaveLength(0);
});
