import { expect, type Page, test } from "@playwright/test";
import { companyIds } from "./identity-api";
import { installOvertimePlanningApi } from "./overtime-planning-api";

test.use({ timezoneId: "Pacific/Honolulu" });
test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(new Date("2026-10-13T12:00:00Z"));
});
const query = `company=${companyIds[0]}&from=2026-10-01&until=2026-10-31`;
const path = `/workforce/overtime/new?${query}`;
async function selectEmployee(page: Page, number = 1) {
  await page.getByRole("button", { name: "Select employee", exact: true }).click();
  const picker = page.getByRole("dialog", { name: "Select employee", exact: true });
  await picker
    .getByRole("button", {
      name: `Select: North Employee ${number} · NORTH-${String(number).padStart(3, "0")}`,
      exact: true,
    })
    .click();
  await expect(picker).toHaveCount(0);
  await expect(page.getByRole("region", { name: "Work schedule", exact: true })).toContainText(
    "Day off",
  );
}
async function open(page: Page) {
  await page.goto(path);
  await page.getByLabel("Work date", { exact: true }).fill("2026-10-12");
  await selectEmployee(page);
}
async function fill(page: Page) {
  await page.getByLabel("Start", { exact: true }).fill("2026-10-12T14:00");
  await page.getByLabel("End", { exact: true }).fill("2026-10-12T17:00");
  await page.getByLabel("Break (minutes)", { exact: true }).fill("15");
  await page.getByLabel("Reason", { exact: true }).fill("Inventory work");
}

test("new overtime uses bounded employee discovery and saves reviewed company-local time", async ({
  page,
}) => {
  const api = await installOvertimePlanningApi(page);
  await page.goto(`/workforce/overtime?${query}&status=PLANNED`);
  await page.getByRole("button", { name: "New overtime request", exact: true }).click();
  await expect(page).toHaveURL(`${path}&status=PLANNED`);
  await page.getByLabel("Work date", { exact: true }).fill("2026-10-12");
  await page.getByRole("button", { name: "Select employee", exact: true }).click();
  const picker = page.getByRole("dialog", { name: "Select employee", exact: true });
  await expect(
    picker.getByRole("table", { name: "Employees", exact: true }).getByRole("row"),
  ).toHaveCount(51);
  await page.screenshot({
    path: "../../.work/dashboard-overtime-picker-light.png",
    animations: "disabled",
  });
  await picker.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(picker.getByRole("table")).toContainText("North Employee 51");
  await expect(picker.getByRole("button", { name: "Next page", exact: true })).toBeDisabled();
  await picker.getByLabel("Search by name or employee number", { exact: true }).fill("NORTH-001");
  await picker.getByRole("button", { name: "Search", exact: true }).click();
  await picker
    .getByRole("button", { name: "Select: North Employee 1 · NORTH-001", exact: true })
    .click();
  await fill(page);
  await page.getByRole("button", { name: "Create request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Overtime request created.");
  expect(api.commits).toBe(1);
  expect(api.writes[0]?.body).toMatchObject({
    expectedEmploymentVersion: 3,
    workDate: "2026-10-12",
    reason: "Inventory work",
    requested: {
      startsAt: "2026-10-12T07:00:00Z",
      endsAt: "2026-10-12T10:00:00Z",
      breakMinutes: 15,
    },
    planningRevision: "a".repeat(64),
  });
  expect(api.planningReads.some((url) => url.searchParams.get("after") === "NORTH-050")).toBe(true);
  await page.getByRole("link", { name: "View request", exact: true }).click();
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText(
    "North Employee 1",
  );
  await expect(page.getByRole("table", { name: "Time records", exact: true })).toContainText("165");
  expect(api.identity.unhandled).toEqual([]);
});

test("read access cannot discover employees or create a request", async ({ page }) => {
  const api = await installOvertimePlanningApi(page, ["overtime.read"]);
  await page.goto(`/workforce/overtime?${query}`);
  await expect(page.getByRole("button", { name: "New overtime request", exact: true })).toHaveCount(
    0,
  );
  await page.goto(path);
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("button", { name: "Select employee", exact: true })).toBeDisabled();
  expect(api.planningReads).toEqual([]);
  expect(api.writes).toEqual([]);
});

test("invalid interval and reason remain editable without reaching HTTP", async ({ page }) => {
  const api = await installOvertimePlanningApi(page);
  await open(page);
  await page.getByRole("button", { name: "Create request", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Enter valid local times");
  await fill(page);
  await page.getByLabel("End", { exact: true }).fill("2026-10-13T03:00");
  await page.getByRole("button", { name: "Create request", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("12-hour");
  await page.getByLabel("End", { exact: true }).fill("2026-10-12T17:00");
  await page.getByLabel("Reason", { exact: true }).fill(" ");
  await page.getByRole("button", { name: "Create request", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("requested time and reason");
  expect(api.writes).toEqual([]);
});

test("changed calendars require protected review and a newly confirmed command", async ({
  page,
}) => {
  const api = await installOvertimePlanningApi(page);
  await open(page);
  await fill(page);
  api.reviseSchedule();
  await page.getByRole("button", { name: "Create request", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("reviewed schedule has changed");
  await expect(page.getByRole("button", { name: "Create request", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Review current schedule", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Stay on this page", exact: true })
    .click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Inventory work");
  await page.getByRole("button", { name: "Review current schedule", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  expect(api.writes).toHaveLength(1);
  await fill(page);
  await page.getByRole("button", { name: "Create request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Overtime request created.");
  expect(api.writes[1]?.body.planningRevision).toBe("b".repeat(64));
  expect(api.writes[1]?.operation).not.toBe(api.writes[0]?.operation);
  expect(api.writes[1]?.body.id).not.toBe(api.writes[0]?.body.id);
});

test("a lost result retains its command through MFA and later conflict without releasing private review", async ({
  page,
}) => {
  const api = await installOvertimePlanningApi(page);
  await open(page);
  await fill(page);
  const reads = api.planningReads.length;
  api.loseNext();
  await page.getByRole("button", { name: "Create request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result could not be confirmed");
  await expect(page.getByLabel("Work date", { exact: true })).toHaveAttribute("readonly", "");
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
  api.rejectWrite("overtime_planning_changed");
  await page.getByRole("button", { name: "Retry this request", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("reviewed schedule has changed");
  expect(api.writes).toHaveLength(3);
  await expect(page.getByRole("region", { name: "Work schedule", exact: true })).toHaveCount(0);
  await expect(page.getByLabel("Reason", { exact: true })).toHaveCount(0);
  const held = api.holdWrite();
  try {
    await page.getByRole("button", { name: "Retry this request", exact: true }).click();
    await held.started;
    await expect(page.getByRole("main")).not.toContainText("North Employee");
    held.release();
    await expect(page.getByRole("status")).toContainText("Overtime request created.");
  } finally {
    held.release();
  }
  expect(api.planningReads).toHaveLength(reads);
  expect(api.writes).toHaveLength(4);
  expect(api.commits).toBe(1);
  for (const write of api.writes) expect(write).toEqual(api.writes[0]);
});

test("denied review stays hidden while subsequent reads are pending or fail", async ({ page }) => {
  const api = await installOvertimePlanningApi(page);
  await open(page);
  api.rejectRead("employee_not_found");
  await page.getByRole("button", { name: "Review current schedule", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("no longer accessible");
  const held = api.holdRead("review");
  api.rejectRead("employee_not_found");
  try {
    await page.getByRole("button", { name: "Retry", exact: true }).click();
    await held.started;
    await expect(page.getByRole("main")).not.toContainText("North Employee");
    held.release();
    await expect(page.getByRole("alert")).toBeVisible();
    await expect(page.getByRole("main")).not.toContainText("North Employee");
  } finally {
    held.release();
  }
});

test("changing date cancels a pending review and clears its previous selection", async ({
  page,
}) => {
  const api = await installOvertimePlanningApi(page);
  await open(page);
  const held = api.holdRead("review");
  try {
    await page.getByRole("button", { name: "Review current schedule", exact: true }).click();
    await held.started;
    await page.getByLabel("Work date", { exact: true }).fill("2026-10-14");
    held.release();
    await expect(page.getByRole("region", { name: "Work schedule", exact: true })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Select employee", exact: true })).toBeEnabled();
    await expect(page.getByRole("main")).not.toContainText("North Employee");
  } finally {
    held.release();
  }
  expect(api.writes).toEqual([]);
});

test("company departure aborts a pending plan and cannot show its receipt in another company", async ({
  page,
}) => {
  const api = await installOvertimePlanningApi(page);
  await open(page);
  await fill(page);
  const held = api.holdWrite();
  try {
    await page.getByRole("button", { name: "Create request", exact: true }).click();
    await held.started;
    await expect(page.getByRole("button", { name: "Create request", exact: true })).toBeDisabled();
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
    held.release();
    await expect(page.getByRole("main")).not.toContainText("North Employee");
    await expect(page.getByRole("main")).not.toContainText("Overtime request created");
    expect(api.writes).toHaveLength(1);
    expect(api.writes[0]?.company).toBe(companyIds[0]);
  } finally {
    held.release();
  }
});

test("English light and Indonesian dark narrow forms preserve their draft and remain contained", async ({
  page,
}) => {
  const api = await installOvertimePlanningApi(page);
  await open(page);
  await fill(page);
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-overtime-plan-light.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("link", { name: "Back to overtime", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Stay on this page", exact: true })
    .click();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue("Inventory work");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-overtime-plan-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Ganti karyawan", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Tinggalkan halaman", exact: true })
    .click();
  const picker = page.getByRole("dialog", { name: "Pilih karyawan", exact: true });
  await expect(picker.getByRole("table", { name: "Karyawan", exact: true })).toBeVisible();
  const bounds = await picker.getByRole("table", { name: "Karyawan", exact: true }).boundingBox();
  expect(bounds?.width).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: "../../.work/dashboard-overtime-picker-dark.png",
    animations: "disabled",
  });
  expect(api.writes).toEqual([]);
  expect(api.identity.unhandled).toEqual([]);
});
