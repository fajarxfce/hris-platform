import { expect, type Page, test } from "@playwright/test";
import { amendmentId } from "./fixtures/payroll-amendments";
import { calculationCompany } from "./fixtures/payroll-calculations";
import { payslipId } from "./fixtures/payroll-payslips";
import { companyIds } from "./identity-api";
import { installPayrollAmendmentAuthoringApi } from "./payroll-amendment-authoring-api";

const directory = `/payroll/amendments?company=${calculationCompany}&year=2026`,
  sources = `/payroll/amendments/new?company=${calculationCompany}&year=2026`,
  create = `/payroll/amendments/new/${payslipId()}?company=${calculationCompany}&year=2026`,
  edit = `/payroll/amendments/${amendmentId()}/edit?company=${calculationCompany}&year=2026`;
async function changeSalary(page: Page, value = "9999999.25") {
  await page.getByRole("button", { name: "Edit month: 2026-09", exact: true }).click();
  const panel = page.getByRole("dialog", { name: "Edit month · 2026-09", exact: true });
  await panel.getByRole("textbox", { name: "Basic salary (IDR)", exact: true }).fill(value);
  await panel.getByRole("button", { name: "Apply changes", exact: true }).click();
  await expect(panel).toHaveCount(0);
}
async function review(page: Page) {
  await page.getByRole("button", { name: "Preview calculation", exact: true }).click();
  await expect(page.getByRole("table", { name: "Monthly comparison", exact: true })).toBeVisible();
  await page
    .getByRole("textbox", { name: "Amendment reason", exact: true })
    .fill("Correct verified remuneration");
}
test("an independent author selects a published source and saves reviewed changes", async ({
  page,
}) => {
  const api = await installPayrollAmendmentAuthoringApi(page);
  await page.goto(directory);
  await page.getByRole("link", { name: "Create amendment", exact: true }).click();
  await expect(page.getByRole("table", { name: "Published basis", exact: true })).toContainText(
    "Amendment employee 1",
  );
  await page
    .getByRole("button", { name: "Select month: Amendment employee 1 · 2026-09", exact: true })
    .click();
  await expect(page.getByRole("button", { name: "Save proposal", exact: true })).toBeDisabled();
  await changeSalary(page);
  await review(page);
  await page.getByRole("button", { name: "Save proposal", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Proposal saved.");
  const write = api.requests.find((row) => row.action === "save");
  expect(write?.input).toMatchObject({
    expectedVersion: null,
    firstAssessmentId: payslipId(),
    corrections: [{ compensation: { basicSalary: "9999999.25" }, input: null }],
  });
  expect(api.count("save")).toBe(1);
  await page.getByRole("link", { name: "View proposal", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Proposal revision 0");
  expect(api.identity.unhandled).toEqual([]);
});
for (const permission of ["payroll.read", "payroll.amend.review", "payroll.amend.publish"])
  test(`${permission} cannot acquire authoring evidence`, async ({ page }) => {
    const api = await installPayrollAmendmentAuthoringApi(page, { permissions: [permission] });
    await page.goto(sources);
    await expect(page.getByRole("alert")).toBeVisible();
    expect(api.count("sources")).toBe(0);
    await expect(page.getByRole("link", { name: "Create amendment", exact: true })).toHaveCount(0);
  });
test("source pages retain annual and literal employee filters", async ({ page }) => {
  const api = await installPayrollAmendmentAuthoringApi(page, { count: 52 });
  await page.goto(sources);
  const table = page.getByRole("table", { name: "Published basis", exact: true });
  await expect(table.getByRole("row")).toHaveCount(51);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(3);
  await page.getByLabel("Employee name or number", { exact: true }).fill("EMP-0001");
  await page.getByRole("button", { name: "Apply filters", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(2);
  await page.getByLabel("Tax year", { exact: true }).fill("2025");
  await page.getByRole("button", { name: "Apply filters", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("No published payroll");
  expect(api.identity.unhandled).toEqual([]);
});
test("complete year references stay read-only before the selected month", async ({ page }) => {
  const api = await installPayrollAmendmentAuthoringApi(page, { multiple: true });
  await page.goto(create);
  await expect(
    page.getByRole("button", { name: "Edit month: 2026-08", exact: true }),
  ).toBeDisabled();
  await changeSalary(page);
  await review(page);
  await expect(
    page.getByRole("table", { name: "Monthly comparison", exact: true }).getByRole("row"),
  ).toHaveCount(3);
  const preview = api.requests.find((row) => row.action === "preview");
  expect(preview?.input).toMatchObject({
    sourceVersions: [
      { assessmentId: payslipId(2), revision: 0 },
      { assessmentId: payslipId(), revision: 0 },
      { assessmentId: payslipId(3), revision: 0 },
    ],
  });
});
test("tax evidence is retained and changed month input invalidates a previous preview", async ({
  page,
}) => {
  const api = await installPayrollAmendmentAuthoringApi(page);
  await page.goto(create);
  await changeSalary(page);
  await review(page);
  await page.getByRole("button", { name: "Edit month: 2026-09", exact: true }).click();
  const panel = page.getByRole("dialog", { name: "Edit month · 2026-09", exact: true });
  await expect(panel.getByRole("combobox", { name: "Tax residency", exact: true })).toBeDisabled();
  await panel.getByRole("checkbox", { name: "Change monthly inputs", exact: true }).check();
  await panel
    .getByRole("textbox", { name: "Taxable non-cash income (IDR)", exact: true })
    .fill("100.25");
  await panel.getByRole("button", { name: "Apply changes", exact: true }).click();
  await expect(page.getByRole("table", { name: "Monthly comparison", exact: true })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Save proposal", exact: true })).toBeDisabled();
  await review(page);
  expect(api.count("preview")).toBe(2);
});
test("month cancellation and page departure protect unapplied input", async ({ page }) => {
  await installPayrollAmendmentAuthoringApi(page);
  await page.goto(create);
  await page.getByRole("button", { name: "Edit month: 2026-09", exact: true }).click();
  const panel = page.getByRole("dialog", { name: "Edit month · 2026-09", exact: true });
  await panel.getByRole("textbox", { name: "Basic salary (IDR)", exact: true }).fill("9000000");
  await panel.getByRole("button", { name: "Close month editor", exact: true }).last().click();
  await expect(
    page.getByRole("dialog", { name: "Discard month changes?", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Keep editing", exact: true }).click();
  await expect(panel.getByRole("textbox", { name: "Basic salary (IDR)", exact: true })).toHaveValue(
    "9000000",
  );
  await panel.getByRole("button", { name: "Apply changes", exact: true }).click();
  await page.getByRole("link", { name: "Back to proposal", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "Leave this page?", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(page.getByRole("table", { name: "Published history", exact: true })).toContainText(
    "Compensation",
  );
});
test("the owning editor protects unapplied month fields during browser navigation", async ({
  page,
}) => {
  await installPayrollAmendmentAuthoringApi(page);
  await page.goto(sources);
  await page
    .getByRole("button", { name: "Select month: Amendment employee 1 · 2026-09", exact: true })
    .click();
  await page.getByRole("button", { name: "Edit month: 2026-09", exact: true }).click();
  const panel = page.getByRole("dialog", { name: "Edit month · 2026-09", exact: true });
  await panel.getByRole("textbox", { name: "Basic salary (IDR)", exact: true }).fill("9000000");
  await page.goBack();
  await expect(page.getByRole("dialog", { name: "Leave this page?", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(panel.getByRole("textbox", { name: "Basic salary (IDR)", exact: true })).toHaveValue(
    "9000000",
  );
  await panel.getByRole("button", { name: "Close month editor", exact: true }).last().click();
  await page
    .getByRole("dialog", { name: "Discard month changes?", exact: true })
    .getByRole("button", { name: "Discard changes", exact: true })
    .click();
  await expect(panel).toHaveCount(0);
  await page.getByRole("link", { name: "Back to proposal", exact: true }).click();
  await expect(page.getByRole("table", { name: "Published basis", exact: true })).toBeVisible();
  await expect(page.getByRole("dialog")).toHaveCount(0);
});

test("an existing proposal requires explicit review of newer publication references", async ({
  page,
}) => {
  const api = await installPayrollAmendmentAuthoringApi(page, { publishedRevision: 1 });
  await page.goto(edit);
  await expect(page.getByRole("status")).toContainText("Published payroll has changed");
  await expect(page.getByRole("button", { name: "Save proposal", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Use these published revisions", exact: true }).click();
  await review(page);
  await page.getByRole("button", { name: "Save proposal", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Proposal saved.");
  expect(api.requests.find((row) => row.action === "save")?.input).toMatchObject({
    expectedVersion: 0,
    sourceVersions: [{ assessmentId: payslipId(), revision: 1 }],
  });
});
test("a lost save response retries the original identifier, operation and payload", async ({
  page,
}) => {
  const api = await installPayrollAmendmentAuthoringApi(page);
  await page.goto(create);
  await changeSalary(page);
  await review(page);
  api.loseNextSave();
  await page.getByRole("button", { name: "Save proposal", exact: true }).click();
  await expect(
    page.getByRole("status").filter({ hasText: "save outcome is unconfirmed" }),
  ).toBeVisible();
  api.failNext("save", "stale_version", 409);
  await page.getByRole("button", { name: "Retry original save", exact: true }).click();
  await expect(
    page.getByRole("status").filter({ hasText: "save outcome is unconfirmed" }),
  ).toBeVisible();
  await expect(
    page.getByRole("textbox", { name: "Amendment reason", exact: true }),
  ).not.toBeEditable();
  await page.getByRole("button", { name: "Retry original save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Proposal saved.");
  const writes = api.requests.filter((row) => row.action === "save");
  expect(writes).toHaveLength(3);
  expect(writes[1]?.input).toEqual(writes[0]?.input);
  expect(writes[2]?.operation).toBe(writes[0]?.operation);
  expect(writes[2]?.url.pathname).toBe(writes[0]?.url.pathname);
  expect(api.receipts.size).toBe(1);
});
test("an initial source conflict preserves input and requires deliberate reload", async ({
  page,
}) => {
  const api = await installPayrollAmendmentAuthoringApi(page);
  await page.goto(create);
  await changeSalary(page);
  await review(page);
  api.setPublishedRevision(1);
  await page.getByRole("button", { name: "Save proposal", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Published payroll has changed");
  await expect(page.getByRole("textbox", { name: "Amendment reason", exact: true })).toHaveValue(
    "Correct verified remuneration",
  );
  await expect(page.getByRole("button", { name: "Save proposal", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Reload published basis", exact: true }).click();
  await page.getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByRole("table", { name: "Published history", exact: true })).toContainText(
    "Unchanged",
  );
  expect(api.count("prepare")).toBe(2);
});
test("pending preparation is discarded when the company changes", async ({ page }) => {
  const api = await installPayrollAmendmentAuthoringApi(page),
    held = api.holdNext("prepare");
  try {
    await page.goto(create);
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    held.release();
    await expect(page.getByRole("status")).toContainText("No amendments");
    await expect(page.getByText("Amendment employee 1", { exact: true })).toHaveCount(0);
  } finally {
    held.release();
  }
});
test("revoked preview access clears private amounts and safe messages omit diagnostics", async ({
  page,
}) => {
  const api = await installPayrollAmendmentAuthoringApi(page);
  await page.goto(create);
  await changeSalary(page);
  api.deny();
  await page.getByRole("button", { name: "Preview calculation", exact: true }).click();
  await expect(page.getByRole("table", { name: "Published history", exact: true })).toHaveCount(0);
  await expect(page.getByText("PRIVATE authoring diagnostic")).toHaveCount(0);
  await expect(page.getByRole("textbox", { name: "Amendment reason", exact: true })).toHaveCount(0);
});
test("narrow dark Indonesian editing retains the proposal without reacquisition", async ({
  page,
}) => {
  const api = await installPayrollAmendmentAuthoringApi(page);
  await page.goto(create);
  await changeSalary(page);
  await review(page);
  const reads = api.requests.length;
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole("textbox", { name: "Alasan koreksi", exact: true })).toHaveValue(
    "Correct verified remuneration",
  );
  await expect(
    page.getByRole("table", { name: "Perbandingan bulanan", exact: true }),
  ).toBeVisible();
  expect(api.requests).toHaveLength(reads);
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    .toBe(true);
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-payroll-amendment-authoring-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
});
