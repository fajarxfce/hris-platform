import { expect, type Page, test } from "@playwright/test";
import { amendmentId } from "./fixtures/payroll-amendments";
import { calculationCompany } from "./fixtures/payroll-calculations";
import { companyIds } from "./identity-api";
import { installPayrollAmendmentsApi } from "./payroll-amendments-api";

const directory = `/payroll/amendments?company=${calculationCompany}&year=2026`,
  detail = `/payroll/amendments/${amendmentId()}?company=${calculationCompany}&year=2026&query=Amendment`;
const revision = (page: Page, label = "Proposal revision", region = "Summary") =>
  page
    .getByRole("region", { name: region, exact: true })
    .getByText(label, { exact: true })
    .locator("..")
    .locator("dd");
for (const permission of ["payroll.amend", "payroll.amend.review", "payroll.amend.publish"]) {
  test(`independent ${permission} can browse amendments without ordinary payroll access`, async ({
    page,
  }) => {
    const api = await installPayrollAmendmentsApi(page, { permissions: [permission] });
    await page.goto(directory);
    await expect(
      page.getByRole("table", { name: "Payroll amendments", exact: true }),
    ).toContainText("Amendment employee 1");
    await expect(page.getByRole("link", { name: "Payslips", exact: true })).toHaveCount(0);
    expect(api.identity.unhandled).toEqual([]);
  });
}
test("annual search and pages retain the selected company and filters", async ({ page }) => {
  const api = await installPayrollAmendmentsApi(page, { count: 52 });
  await page.goto(directory);
  const table = page.getByRole("table", { name: "Payroll amendments", exact: true });
  await expect(table.getByRole("row")).toHaveCount(51);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(3);
  await page.getByLabel("Employee name or number", { exact: true }).fill("EMP-0001");
  await page.getByRole("button", { name: "Apply filters", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(2);
  await page
    .getByRole("button", {
      name: "View amendment: Amendment employee 1 · 2026-09 · Proposal revision 1",
      exact: true,
    })
    .click();
  await expect(revision(page)).toHaveText("1");
  await page.getByRole("link", { name: "All amendments", exact: true }).click();
  await expect(page.getByLabel("Employee name or number", { exact: true })).toHaveValue("EMP-0001");
  await page.getByLabel("Status", { exact: true }).selectOption("CANCELLED");
  await page.getByRole("button", { name: "Apply filters", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("No amendments");
  expect(api.identity.unhandled).toEqual([]);
});
test("tabs reuse their acquired data and revision navigation keeps original calculation evidence", async ({
  page,
}) => {
  const api = await installPayrollAmendmentsApi(page);
  await page.goto(detail);
  await expect(revision(page)).toHaveText("1");
  const reads = api.reads.length;
  await page.getByRole("tab", { name: "Calculation", exact: true }).click();
  await expect(page.getByRole("table", { name: "Monthly comparison", exact: true })).toContainText(
    "-IDR",
  );
  await page.getByRole("tab", { name: "Proposal", exact: true }).click();
  await page
    .getByRole("tabpanel", { name: "Proposal", exact: true })
    .getByText("2026-09", { exact: true })
    .click();
  await expect(page.getByRole("region", { name: "Earnings", exact: true })).toBeVisible();
  await page.getByRole("tab", { name: "History", exact: true }).click();
  expect(api.reads).toHaveLength(reads);
  await page
    .getByRole("button", { name: "View revision: Proposal revision 0", exact: true })
    .click();
  await expect(page.getByRole("status")).toContainText("Retained proposal");
  await page.getByRole("tab", { name: "Overview", exact: true }).click();
  await expect(revision(page)).toHaveText("0");
  await page.getByRole("tab", { name: "Calculation", exact: true }).click();
  await expect(page.getByRole("tabpanel").getByRole("status")).toContainText(
    "No calculation was retained",
  );
  await page.getByRole("link", { name: "Current proposal", exact: true }).click();
  await expect(page.getByRole("table", { name: "Monthly comparison", exact: true })).toBeVisible();
  await page.goBack();
  await expect(page.getByRole("tabpanel").getByRole("status")).toContainText(
    "No calculation was retained",
  );
  expect(api.count("revisions")).toBe(2);
  expect(api.identity.unhandled).toEqual([]);
});
test("calculation basis is explicit after later edits and can be inspected", async ({ page }) => {
  const api = await installPayrollAmendmentsApi(page, { version: 2 });
  await page.goto(`${detail}&tab=calculation`);
  await expect(revision(page, "Calculated from proposal revision", "Calculation")).toHaveText("0");
  await page.getByRole("link", { name: "View calculation basis", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Proposal revision 0");
  expect(new URL(page.url()).searchParams.get("tab")).toBe("proposal");
  expect(api.count("revisions")).toBe(1);
});
test("history pages have a finite cursor and each selected revision remains explicit", async ({
  page,
}) => {
  await installPayrollAmendmentsApi(page, { version: 30 });
  await page.goto(`${detail}&tab=history`);
  const history = page.getByRole("table", { name: "History", exact: true });
  await expect(history.getByRole("row")).toHaveCount(26);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(history.getByRole("row")).toHaveCount(7);
  await expect(page.getByRole("button", { name: "Next page", exact: true })).toBeDisabled();
  await page
    .getByRole("button", { name: "View revision: Proposal revision 25", exact: true })
    .click();
  await expect(page.getByRole("status")).toContainText("Proposal revision 25");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Proposal revision 25");
});
test("failed rule attempts remain inspectable and do not look published", async ({ page }) => {
  await installPayrollAmendmentsApi(page, { failedCalculation: true });
  await page.goto(`${detail}&tab=calculation`);
  await expect(page.getByRole("alert")).toContainText("Prepare effective compensation");
  await expect(page.getByRole("table", { name: "Monthly comparison", exact: true })).toHaveCount(0);
  await page.getByRole("tab", { name: "History", exact: true }).click();
  await expect(page.getByRole("table", { name: "History", exact: true })).toContainText(
    "Calculation failed",
  );
});
for (const query of ["revision=bad", "revision=-1", "revision=0&revision=1"]) {
  test(`invalid ${query} cannot silently load another proposal`, async ({ page }) => {
    const api = await installPayrollAmendmentsApi(page);
    await page.goto(`${detail}&${query}`);
    await expect(page.getByRole("alert")).toBeVisible();
    expect(api.reads).toHaveLength(0);
    await page.getByRole("link", { name: "Current proposal", exact: true }).click();
    await expect(revision(page)).toHaveText("1");
  });
}
test("a late retained read cannot replace the current proposal", async ({ page }) => {
  const api = await installPayrollAmendmentsApi(page),
    held = api.holdNext("revisions");
  try {
    await page.goto(`${detail}&revision=0`);
    await held.entered;
    await page.getByRole("link", { name: "Current proposal", exact: true }).click();
    await expect(revision(page)).toHaveText("1");
    held.release();
    await expect(revision(page)).toHaveText("1");
  } finally {
    held.release();
  }
});
test("revoked access clears private evidence and never displays raw diagnostics", async ({
  page,
}) => {
  const api = await installPayrollAmendmentsApi(page);
  await page.goto(`${detail}&tab=calculation`);
  await expect(page.getByRole("table", { name: "Monthly comparison", exact: true })).toBeVisible();
  api.deny();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("table", { name: "Monthly comparison", exact: true })).toHaveCount(0);
  await expect(page.getByText("PRIVATE diagnostic sentinel")).toHaveCount(0);
});
test("read recovery and narrow dark Indonesian review retain their selected scope", async ({
  page,
}) => {
  const api = await installPayrollAmendmentsApi(page);
  api.failNext("get", "connection_unavailable");
  await page.goto(detail);
  await expect(page.getByRole("alert")).toBeVisible();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(revision(page)).toHaveText("1");
  const reads = api.reads.length;
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(revision(page, "Revisi proposal", "Ringkasan")).toHaveText("1");
  await page.getByRole("tab", { name: "Perhitungan", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Perbandingan bulanan", exact: true }),
  ).toBeVisible();
  expect(api.reads).toHaveLength(reads);
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    .toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-payroll-amendment-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
});

test("company replacement discards a pending private calculation", async ({ page }) => {
  const api = await installPayrollAmendmentsApi(page);
  await page.goto(detail);
  await expect(revision(page)).toHaveText("1");
  const held = api.holdNext("calculations");
  try {
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("status")).toContainText("No amendments");
    held.release();
    await expect(page.getByRole("status")).toContainText("No amendments");
    await expect(page.getByText("Amendment employee 1", { exact: true })).toHaveCount(0);
    expect(new URL(page.url()).searchParams.get("company")).toBe(companyIds[1]);
  } finally {
    held.release();
  }
});
