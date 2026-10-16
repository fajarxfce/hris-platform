import { expect, type Page, test } from "@playwright/test";
import { amendmentId } from "./fixtures/payroll-amendments";
import { calculationCompany } from "./fixtures/payroll-calculations";
import { payslipId } from "./fixtures/payroll-payslips";
import { workAdjustmentId } from "./fixtures/payroll-work";
import { installPayrollAmendmentAuthoringApi } from "./payroll-amendment-authoring-api";

const create = `/payroll/amendments/new/${payslipId()}?company=${calculationCompany}&year=2026`,
  edit = `/payroll/amendments/${amendmentId()}/edit?company=${calculationCompany}&year=2026`;
async function chooseAttendance(page: Page, revision: number) {
  await page.getByRole("button", { name: "Edit month: 2026-09", exact: true }).click();
  const panel = page.getByRole("dialog", { name: "Edit month · 2026-09", exact: true });
  await panel.getByRole("checkbox", { name: "Change compensation", exact: true }).uncheck();
  await panel
    .getByRole("combobox", { name: "Attendance source", exact: true })
    .selectOption(workAdjustmentId(revision));
  await panel.getByRole("button", { name: "Apply changes", exact: true }).click();
  await expect(panel).toHaveCount(0);
}
async function preview(page: Page) {
  await page.getByRole("button", { name: "Preview calculation", exact: true }).click();
  await expect(page.getByRole("table", { name: "Monthly comparison", exact: true })).toBeVisible();
  await page
    .getByRole("textbox", { name: "Amendment reason", exact: true })
    .fill("Reviewed attendance correction");
}
test("an attendance-only proposal retains its source after a lost save and newer HR correction", async ({
  page,
}) => {
  const api = await installPayrollAmendmentAuthoringApi(page, { attendanceRevision: 1 });
  await page.goto(create);
  await chooseAttendance(page, 1);
  await preview(page);
  await page.locator("summary").filter({ hasText: "Attendance used · 2026-09" }).click();
  const evidence = page.getByRole("table", { name: "Attendance used · 2026-09", exact: true });
  await expect(evidence.getByRole("row").filter({ hasText: "2026-09-01" })).toContainText("Worked");
  api.loseNextSave();
  await page.getByRole("button", { name: "Save proposal", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Retry original save", exact: true }),
  ).toBeVisible();
  api.setAttendanceRevision(2);
  await page.getByRole("button", { name: "Retry original save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Proposal saved.");
  const writes = api.requests.filter((row) => row.action === "save");
  expect(writes).toHaveLength(2);
  expect(writes[1]?.input).toEqual(writes[0]?.input);
  expect(writes[1]?.operation).toBe(writes[0]?.operation);
  expect(writes[0]?.input).toMatchObject({
    corrections: [
      {
        assessmentId: payslipId(),
        compensation: null,
        input: null,
        workAdjustmentId: workAdjustmentId(),
      },
    ],
  });
  expect(api.receipts.size).toBe(1);
  await page.getByRole("link", { name: "View proposal", exact: true }).click();
  await page.locator("summary").filter({ hasText: "2026-09" }).click();
  await expect(page.getByText(workAdjustmentId(), { exact: true })).toBeVisible();
});
test("reopening a stale draft keeps its selected attendance until the author chooses a replacement", async ({
  page,
}) => {
  const api = await installPayrollAmendmentAuthoringApi(page, {
    attendanceRevision: 2,
    retainedAttendanceRevision: 1,
  });
  await page.goto(edit);
  await page.getByRole("button", { name: "Edit month: 2026-09", exact: true }).click();
  const panel = page.getByRole("dialog", { name: "Edit month · 2026-09", exact: true }),
    selection = panel.getByRole("combobox", { name: "Attendance source", exact: true });
  await expect(selection).toHaveValue(workAdjustmentId());
  await expect(panel.getByRole("status")).toContainText("Attendance has changed.");
  await panel.getByRole("button", { name: "Apply changes", exact: true }).click();
  await expect(panel.getByRole("alert")).toContainText("The attendance source has changed.");
  expect(api.count("preview")).toBe(0);
  await selection.selectOption(workAdjustmentId(2));
  await panel.getByRole("button", { name: "Apply changes", exact: true }).click();
  await preview(page);
  expect(api.requests.find((row) => row.action === "preview")?.input).toMatchObject({
    corrections: [{ workAdjustmentId: workAdjustmentId(2) }],
  });
});
test("a definite attendance conflict requires source review and never retries a substituted proposal", async ({
  page,
}) => {
  const api = await installPayrollAmendmentAuthoringApi(page, { attendanceRevision: 1 });
  await page.goto(create);
  await chooseAttendance(page, 1);
  await preview(page);
  api.setAttendanceRevision(2);
  await page.getByRole("button", { name: "Save proposal", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("The attendance source has changed.");
  await expect(page.getByRole("button", { name: "Retry original save", exact: true })).toHaveCount(
    0,
  );
  await expect(page.getByRole("button", { name: "Save proposal", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Reload published basis", exact: true }).click();
  await page.getByRole("button", { name: "Leave page", exact: true }).click();
  await chooseAttendance(page, 2);
  await preview(page);
  await page.getByRole("button", { name: "Save proposal", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Proposal saved.");
  const writes = api.requests.filter((row) => row.action === "save");
  expect(writes).toHaveLength(2);
  expect(writes[1]?.operation).not.toBe(writes[0]?.operation);
  expect(writes[1]?.input).toMatchObject({
    corrections: [{ workAdjustmentId: workAdjustmentId(2) }],
  });
});
test("attendance references remain readable in a narrow Indonesian drawer with doubled text", async ({
  page,
}) => {
  const api = await installPayrollAmendmentAuthoringApi(page, {
    attendanceRevision: 2,
    retainedAttendanceRevision: 1,
  });
  await page.goto(edit);
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("button", { name: "Edit bulan: 2026-09", exact: true }).click();
  const panel = page.getByRole("dialog", { name: "Edit bulan · 2026-09", exact: true });
  const reads = api.requests.length;
  await panel.evaluate((node) => {
    const sizes = [...node.querySelectorAll<HTMLElement>("*")].map((element) => ({
      element,
      size: Number.parseFloat(getComputedStyle(element).fontSize),
      height: Number.parseFloat(getComputedStyle(element).lineHeight),
    }));
    for (const { element, size, height } of sizes) {
      if (Number.isFinite(size)) element.style.fontSize = `${size * 2}px`;
      if (Number.isFinite(height)) element.style.lineHeight = `${height * 2}px`;
    }
  });
  await expect(panel.getByRole("combobox", { name: "Sumber attendance", exact: true })).toHaveValue(
    workAdjustmentId(),
  );
  await expect
    .poll(() =>
      panel
        .getByRole("combobox", { name: "Sumber attendance", exact: true })
        .evaluate(
          (element) =>
            element.getBoundingClientRect().height >=
            Number.parseFloat(getComputedStyle(element).lineHeight) + 12,
        ),
    )
    .toBe(true);
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    .toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-payroll-amendment-attendance-narrow.png",
    animations: "disabled",
  });
  expect(api.requests).toHaveLength(reads);
});
