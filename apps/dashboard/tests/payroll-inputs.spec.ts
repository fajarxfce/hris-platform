import { expect, type Page, test } from "@playwright/test";
import { inputAdjustment, inputEmployee } from "./fixtures/payroll-inputs";
import { companyIds } from "./identity-api";
import { installPayrollInputApi } from "./payroll-input-api";

const directory = `/payroll/inputs?company=${companyIds[0]}&month=2026-09`,
  detail = `/payroll/inputs/${inputEmployee}?company=${companyIds[0]}&month=2026-09`,
  edit = `/payroll/inputs/${inputEmployee}/edit?company=${companyIds[0]}&month=2026-09`;
async function fillBasic(page: Page) {
  await page.goto(edit);
  await expect(page.getByLabel("Taxable non-cash income (IDR)", { exact: true })).toBeVisible();
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed monthly input");
}
test("directory filters and overview/history tabs retain coherent data", async ({ page }) => {
  const api = await installPayrollInputApi(page);
  await page.goto(directory);
  await page
    .getByRole("button", { name: "Open input: Payroll Input Employee", exact: true })
    .click();
  await expect(page.getByRole("region", { name: "Attendance source", exact: true })).toContainText(
    "Closed",
  );
  const reads = api.reads.length;
  await page.getByRole("tab", { name: "History", exact: true }).click();
  expect(api.reads).toHaveLength(reads);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.getByRole("button", { name: "View revision: Revision 0", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText("Prepare input revision 0");
  await page.getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("tab", { name: "Overview", exact: true }).click();
  expect(api.reads).toHaveLength(reads + 1);
  await page.getByRole("link", { name: "Monthly inputs", exact: true }).click();
  await page.getByRole("combobox", { name: "Status", exact: true }).selectOption("VERIFIED");
  await page.getByRole("button", { name: "Apply filters", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("No employees match these filters.");
});
test("new input supports earnings deductions days and holiday evidence", async ({ page }) => {
  const api = await installPayrollInputApi(page, ["payroll.calculate"], true);
  await page.goto(edit.replace("2026-09", "2026-03"));
  await page.getByLabel("Taxable non-cash income (IDR)", { exact: true }).fill("0");
  await page.getByRole("button", { name: "Add earning", exact: true }).click();
  const earning = page.getByRole("group", { name: "Variable earnings 1", exact: true });
  await earning.getByLabel("Code", { exact: true }).fill("bonus");
  await earning.getByLabel("Name", { exact: true }).fill("Monthly bonus");
  await earning.getByLabel("Amount (IDR)", { exact: true }).fill("2000000.25");
  await page.getByRole("button", { name: "Add deduction", exact: true }).click();
  const deduction = page.getByRole("group", { name: "Net deductions 1", exact: true });
  await deduction.getByLabel("Code", { exact: true }).fill("MEAL");
  await deduction.getByLabel("Name", { exact: true }).fill("Meal adjustment");
  await deduction.getByLabel("Amount (IDR)", { exact: true }).fill("125000.50");
  await page.getByLabel("Scheduled month units", { exact: true }).fill("22.5");
  await page.getByRole("button", { name: "Add day resolution", exact: true }).click();
  const day = page.getByRole("group", { name: "Day resolutions 1", exact: true });
  await day.getByLabel("Work date", { exact: true }).fill("2026-03-02");
  await day.getByLabel("Day portion", { exact: true }).selectOption("FIRST_HALF");
  await day.getByLabel("Pay treatment", { exact: true }).selectOption("PAID");
  await day.getByLabel("Reference", { exact: true }).fill("Day review");
  await page.getByRole("checkbox", { name: "Include holiday allowance", exact: true }).check();
  const holiday = page.getByRole("group", { name: "Holiday allowance (THR)", exact: true });
  await holiday.getByLabel("Holiday", { exact: true }).selectOption("EID_FITR");
  await holiday.getByLabel("Holiday date", { exact: true }).fill("2026-03-20");
  await holiday.getByLabel("Continuous service from", { exact: true }).fill("2025-01-01");
  await holiday.getByLabel("Prior payment", { exact: true }).selectOption("NOT_PAID");
  await holiday.getByLabel("Promised amount (IDR)", { exact: true }).fill("3000000");
  await holiday.getByLabel("Review reference", { exact: true }).fill("THR review");
  await page.getByLabel("Review reference", { exact: true }).last().fill("Monthly input review");
  await page.getByLabel("Reason", { exact: true }).fill("Prepare March input");
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Input saved.");
  expect(api.writes[0]?.body).toMatchObject({
    expectedVersion: null,
    expectedEmploymentVersion: 7,
    expectedWorkPeriodVersion: 2,
    terms: {
      earnings: [{ code: "BONUS", amount: "2000000.25" }],
      deductions: [{ code: "MEAL", amount: "125000.50" }],
      scheduledMonthUnits: "22.5",
      dayResolutions: [{ workDate: "2026-03-02", portion: "FIRST_HALF" }],
      holidayAllowance: { kind: "EID_FITR", promisedAmount: "3000000" },
    },
  });
  await page.getByRole("link", { name: "View input", exact: true }).click();
  await expect(
    page.getByRole("region", { name: "Holiday allowance (THR)", exact: true }),
  ).toContainText("THR review");
});
test("lost save response retains the command after a rejected retry", async ({ page }) => {
  const api = await installPayrollInputApi(page);
  await fillBasic(page);
  api.dropNext();
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result is unconfirmed.");
  api.failNext("stale_version");
  await page.getByRole("button", { name: "Retry request", exact: true }).click();
  await expect(page.getByRole("button", { name: "Retry request", exact: true })).toBeEnabled();
  await page.getByRole("button", { name: "Retry request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Input saved.");
  expect(api.writes).toHaveLength(3);
  expect(api.writes[1]).toEqual(api.writes[0]);
  expect(api.writes[2]).toEqual(api.writes[0]);
});
test("changed attendance requires protected reload before a new command", async ({ page }) => {
  const api = await installPayrollInputApi(page);
  await fillBasic(page);
  api.advanceSource();
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(page.getByRole("button", { name: "Reload review", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  await page.getByLabel("Reason", { exact: true }).fill("Review updated source");
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Input saved.");
  expect(api.writes[1]?.body.expectedWorkPeriodVersion).toBe(3);
  expect(api.writes[1]?.operation).not.toBe(api.writes[0]?.operation);
});
test("independent verification displays the reviewed version and recovers its receipt", async ({
  page,
}) => {
  const api = await installPayrollInputApi(page, ["payroll.review"]);
  await page.goto(detail);
  await expect(page.getByRole("link", { name: "Edit input", exact: true })).toHaveCount(0);
  await page.getByRole("link", { name: "Verify input", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Verify monthly input", exact: true }),
  ).toBeVisible();
  await page.getByLabel("Reason", { exact: true }).fill("Independent review of September input");
  api.dropNext();
  await page.getByRole("button", { name: "Verify input", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result is unconfirmed.");
  await page.getByRole("button", { name: "Retry request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Input verified.");
  expect(api.writes[0]?.body).toEqual({
    expectedVersion: 2,
    reason: "Independent review of September input",
  });
  expect(api.writes[1]).toEqual(api.writes[0]);
  await page.getByRole("link", { name: "View input", exact: true }).click();
  await expect(page.getByRole("link", { name: "Verify input", exact: true })).toHaveCount(0);
});
test("locale and theme preserve the draft on a narrow viewport", async ({ page }) => {
  const api = await installPayrollInputApi(page);
  await fillBasic(page);
  const reads = api.reads.length;
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue("Reviewed monthly input");
  expect(api.reads).toHaveLength(reads);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-payroll-input-editor-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
});
test("readers cannot prepare and company replacement clears the prior input", async ({ page }) => {
  await installPayrollInputApi(page, ["payroll.read"]);
  await page.goto(detail);
  await expect(page.getByRole("link", { name: "Edit input", exact: true })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Verify input", exact: true })).toHaveCount(0);
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  await expect(
    page.getByRole("table", { name: "Monthly payroll inputs", exact: true }),
  ).toContainText("South Payroll Employee");
  await expect(page.getByText("Prepare input revision 2", { exact: true })).toHaveCount(0);
});

test("adjusted input recovery retains its selected evidence after another correction", async ({
  page,
}) => {
  const api = await installPayrollInputApi(page);
  const first = api.adjustAttendance();
  await fillBasic(page);
  await expect(page.getByRole("region", { name: "Attendance source", exact: true })).toContainText(
    "Attendance revision 1",
  );
  api.dropNext();
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result is unconfirmed.");
  api.adjustAttendance();
  await page.getByRole("button", { name: "Retry request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Input saved.");
  expect(api.writes[0]?.body.workAdjustmentId).toBe(first.id);
  expect(api.writes[1]).toEqual(api.writes[0]);
  await page.getByRole("link", { name: "View input", exact: true }).click();
  await expect(page.getByRole("region", { name: "Attendance source", exact: true })).toContainText(
    "Attendance revision 2",
  );
  await expect(page.getByRole("region", { name: "Revision 3", exact: true })).toContainText(
    first.id,
  );
  await expect(
    page.getByText(
      "The attendance or employment version has changed. Prepare a new input before verification.",
      { exact: true },
    ),
  ).toBeVisible();
});

test("attendance revision changes require a new explicit form review", async ({ page }) => {
  const api = await installPayrollInputApi(page);
  api.adjustAttendance();
  await fillBasic(page);
  const next = api.adjustAttendance();
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByRole("region", { name: "Attendance source", exact: true })).toContainText(
    "Attendance revision 2",
  );
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed updated attendance");
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Input saved.");
  expect(api.writes[0]?.body.workAdjustmentId).toBe(inputAdjustment().id);
  expect(api.writes[1]?.body.workAdjustmentId).toBe(next.id);
  expect(api.writes[1]?.operation).not.toBe(api.writes[0]?.operation);
});

test("independent review displays adjusted evidence and rejects a later source change", async ({
  page,
}) => {
  const api = await installPayrollInputApi(page, ["payroll.review"]);
  const selected = api.adjustAttendance();
  const current = api.revisions.at(-1);
  expect(current).toBeDefined();
  if (current) current.workAdjustmentId = selected.id;
  await page.goto(detail);
  await page.getByRole("link", { name: "Verify input", exact: true }).click();
  await expect(page.getByRole("region", { name: "Attendance source", exact: true })).toContainText(
    selected.reason,
  );
  await page.getByLabel("Reason", { exact: true }).fill("Independent attendance source review");
  api.adjustAttendance();
  await page.getByRole("button", { name: "Verify input", exact: true }).click();
  await expect(page.getByRole("button", { name: "Reload review", exact: true })).toBeVisible();
  expect(api.writes).toHaveLength(1);
});

test("adjusted source details remain readable with enlarged text in the narrow dark layout", async ({
  page,
}) => {
  const api = await installPayrollInputApi(page);
  api.adjustAttendance();
  await page.setViewportSize({ width: 390, height: 844 });
  await fillBasic(page);
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.evaluate(() => {
    const nodes = [
      ...document.querySelectorAll<HTMLElement>(".app-report-content, .app-report-content *"),
    ].map((node) => {
      const style = getComputedStyle(node);
      return {
        node,
        font: Number.parseFloat(style.fontSize),
        line: Number.parseFloat(style.lineHeight),
      };
    });
    for (const { node, font, line } of nodes) {
      if (Number.isFinite(font)) node.style.fontSize = `${font * 2}px`;
      if (Number.isFinite(line)) node.style.lineHeight = `${line * 2}px`;
    }
  });
  await expect(page.getByRole("region", { name: "Sumber attendance", exact: true })).toContainText(
    "Revisi attendance 1",
  );
  expect(
    await page.getByRole("region", { name: "Sumber attendance", exact: true }).evaluate((panel) =>
      [...panel.querySelectorAll(".app-property-row")].every((row, index, rows) => {
        const label = row.querySelector("dt")?.getBoundingClientRect();
        const value = row.querySelector("dd")?.getBoundingClientRect();
        const previous = rows[index - 1]?.getBoundingClientRect();
        return (
          label && value && value.top >= label.bottom && (!previous || label.top >= previous.bottom)
        );
      }),
    ),
  ).toBe(true);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-payroll-attendance-source-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Simpan draft", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Input disimpan.");
});
