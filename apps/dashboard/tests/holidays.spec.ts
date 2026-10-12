import { expect, type Page, test } from "@playwright/test";
import { installHolidaysApi, newYearId } from "./holidays-api";
import { companyIds } from "./identity-api";

test.use({ timezoneId: "Pacific/Honolulu" });
test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(new Date("2026-10-11T12:00:00Z"));
});
const listPath = "/workforce/holidays?from=2026-01-01&until=2026-12-31";
const departure = (page: Page) =>
  page.getByRole("dialog", { name: "Leave this page?", exact: true });
async function createForm(page: Page) {
  await page.goto(listPath);
  await page.getByRole("link", { name: "Create holiday", exact: true }).click();
  await page.getByLabel("Date", { exact: true }).fill("2026-03-20");
  await page.getByLabel("Name", { exact: true }).fill(" Company observance ");
  await page.getByLabel("Reason", { exact: true }).fill(" Annual calendar ");
}

test("holiday dates and edits survive browser timezone, language, theme and mobile layout", async ({
  page,
}) => {
  const api = await installHolidaysApi(page);
  await page.goto(listPath);
  await expect(page.getByRole("table")).toContainText("Jan 1, 2026");
  await expect(page.getByRole("table").getByRole("row")).toHaveCount(3);
  const reads = api.reads.length;
  await page.getByLabel("Until", { exact: true }).fill("2026-01-31");
  expect(api.reads).toHaveLength(reads);
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page.getByRole("table").getByRole("row")).toHaveCount(2);
  await page.getByRole("link", { name: "Create holiday", exact: true }).click();
  await page.getByLabel("Date", { exact: true }).fill("2026-03-20");
  await page.getByLabel("Name", { exact: true }).fill(" Company observance ");
  await page.getByLabel("Reason", { exact: true }).fill(" Annual calendar ");
  await expect(page.getByRole("button", { name: "Save", exact: true })).toBeEnabled();
  await page.screenshot({ path: "../../.work/dashboard-holiday-light.png", fullPage: true });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByLabel("Nama", { exact: true })).toHaveValue(" Company observance ");
  await expect(page.getByLabel("Tanggal", { exact: true })).toHaveValue("2026-03-20");
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
    .toBe(true);
  await page.screenshot({ path: "../../.work/dashboard-holiday-dark-mobile.png", fullPage: true });
  await page.getByRole("button", { name: "Simpan", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Hari libur tersimpan.");
  expect(api.writes).toHaveLength(1);
  expect(api.commits).toBe(1);
  expect(api.writes[0]?.body).toEqual({
    workDate: "2026-03-20",
    name: "Company observance",
    active: true,
    expectedVersion: null,
    reason: "Annual calendar",
  });
  expect(api.writes[0]?.csrf).toMatch(/^fixture-csrf-/u);
  await page.getByRole("link", { name: "Lihat detail", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Company observance", exact: true }),
  ).toBeVisible();
  await expect(page.getByText("20 Maret 2026", { exact: true })).toBeVisible();
  expect(api.identity.unhandled).toHaveLength(0);
});

test("an unknown holiday save retains its request through MFA and requires an explicit retry", async ({
  page,
}) => {
  const api = await installHolidaysApi(page);
  await createForm(page);
  api.loseNextSave();
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save outcome is not confirmed");
  await expect(page.getByLabel("Name", { exact: true })).toHaveJSProperty("readOnly", true);
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
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue(" Company observance ");
  expect(api.writes).toHaveLength(2);
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Holiday saved.");
  expect(api.commits).toBe(1);
  expect(api.writes).toHaveLength(3);
  for (const write of api.writes) {
    expect(write.operation).toBe(api.writes[0]?.operation);
    expect(write.id).toBe(api.writes[0]?.id);
    expect(write.body).toEqual(api.writes[0]?.body);
  }
  await expect(page.getByText("PRIVATE TECHNICAL ERROR", { exact: true })).toHaveCount(0);
});

test("reload discards confirmed edits even for unchanged values and handles a competing calendar revision", async ({
  page,
}) => {
  const api = await installHolidaysApi(page);
  await page.goto(`/workforce/holidays/${newYearId}/edit`);
  await page.getByLabel("Name", { exact: true }).fill("Draft");
  await page.getByRole("button", { name: "Reload latest", exact: true }).click();
  await departure(page).getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("Draft");
  await page.getByRole("button", { name: "Reload latest", exact: true }).click();
  await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("North holiday");
  await page.getByLabel("Name", { exact: true }).fill("My revision");
  await page.getByLabel("Reason", { exact: true }).fill("Change observance");
  api.replaceHoliday("Concurrent revision", "2026-01-02", 3);
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("This record has changed");
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("My revision");
  await page.getByRole("button", { name: "Reload latest", exact: true }).click();
  await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("Concurrent revision");
  await expect(page.getByLabel("Date", { exact: true })).toHaveValue("2026-01-02");
  await page.getByLabel("Status", { exact: true }).selectOption("false");
  await page.getByLabel("Reason", { exact: true }).fill("Deactivate observance");
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Holiday saved.");
  expect(api.writes.map((write) => write.body.expectedVersion)).toEqual([2, 3]);
  expect(api.writes[1]?.body.active).toBe(false);
  expect(api.writes[0]?.operation).not.toBe(api.writes[1]?.operation);
});

test("a pending save protects company departure and late acknowledgement cannot restore its screen", async ({
  page,
}) => {
  const api = await installHolidaysApi(page);
  await createForm(page);
  const pending = api.holdSave();
  try {
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await pending.started;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(departure(page)).toBeVisible();
    await departure(page).getByRole("button", { name: "Stay on this page", exact: true }).click();
    await expect(page.getByRole("combobox", { name: "Company", exact: true })).toHaveValue(
      companyIds[0],
    );
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
    pending.release();
    await expect(page.getByRole("table")).toContainText("South holiday");
    await expect(page.getByRole("status").filter({ hasText: "Holiday saved." })).toHaveCount(0);
    expect(api.writes).toHaveLength(1);
    expect(api.writes[0]?.company).toBe(companyIds[0]);
  } finally {
    pending.release();
  }
});

test("company read access shows holidays but hides mutations and rejects a pinned foreign detail", async ({
  page,
}) => {
  const api = await installHolidaysApi(page, { permissions: ["company.read"] });
  await page.goto(`/workforce/holidays/${newYearId}?company=${companyIds[1]}`);
  await expect(page.getByRole("table")).toContainText("North holiday");
  expect(api.reads.every((url) => url.pathname.includes(companyIds[0]))).toBe(true);
  await expect(page.getByRole("link", { name: "Create holiday", exact: true })).toHaveCount(0);
  await page
    .getByRole("button", { name: "View details: North holiday (2026-01-01)", exact: true })
    .click();
  await expect(page.getByRole("heading", { name: "North holiday", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Edit holiday", exact: true })).toHaveCount(0);
  await page.goto("/workforce/holidays/new");
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("button", { name: "Save", exact: true })).toHaveCount(0);
  expect(api.writes).toHaveLength(0);
});

test("invalid date ranges stop before transport and a locked work period remains a definite rejection", async ({
  page,
}) => {
  const api = await installHolidaysApi(page);
  await page.goto(listPath);
  await expect(page.getByRole("table")).toBeVisible();
  const reads = api.reads.length;
  await page.getByLabel("Until", { exact: true }).fill("2027-12-31");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Review the date range");
  expect(api.reads).toHaveLength(reads);
  await page.getByLabel("From", { exact: true }).fill("2026-03-01");
  await page.getByLabel("Until", { exact: true }).fill("2026-03-31");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("No holidays in this date range");
  await createForm(page);
  api.rejectNextSave("work_period_locked");
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("This attendance period is locked");
  await expect(page.getByLabel("Name", { exact: true })).toHaveJSProperty("readOnly", false);
  expect(api.commits).toBe(0);
  await expect(page.getByRole("button", { name: "Retry save", exact: true })).toHaveCount(0);
});
