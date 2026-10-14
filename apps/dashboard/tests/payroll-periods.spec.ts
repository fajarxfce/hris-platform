import { expect, type Page, test } from "@playwright/test";
import { periodEmployee, periodId } from "./fixtures/payroll-periods";
import { companyIds } from "./identity-api";
import { installPayrollPeriodsApi } from "./payroll-periods-api";

const directory = `/payroll/periods?company=${companyIds[0]}&from=2026-01&until=2026-12`,
  detail = `/payroll/periods/${periodId}?company=${companyIds[0]}`,
  creation = `/payroll/periods/new?company=${companyIds[0]}&month=2026-10`,
  cancel = `/payroll/periods/${periodId}/cancel?company=${companyIds[0]}`;
async function fillCreation(page: Page) {
  await page.goto(creation);
  await expect(page.getByRole("table", { name: "Employees", exact: true })).toBeVisible();
  await page.getByLabel("Planned payment date", { exact: true }).fill("2026-11-02");
  await page.getByLabel("Reason", { exact: true }).fill("Prepare October payroll");
  await page
    .getByRole("checkbox", { name: "Select employee: Payroll Employee 001", exact: true })
    .check();
}
test("period directory and separate roster/history tabs preserve bounded reads", async ({
  page,
}) => {
  const api = await installPayrollPeriodsApi(page);
  await page.goto(directory);
  await expect(page.getByRole("table", { name: "Payroll periods", exact: true })).toContainText(
    "2026-09",
  );
  await page.getByRole("button", { name: "Open period: 2026-09 · Draft", exact: true }).click();
  const table = page.getByRole("table", { name: "Employees", exact: true });
  await expect(table.getByRole("row")).toHaveCount(51);
  const reads = api.reads.length;
  await page.getByRole("tab", { name: "History", exact: true }).click();
  await expect(page.getByRole("table", { name: "History", exact: true })).toContainText(
    "Prepare September payroll",
  );
  expect(api.reads).toHaveLength(reads);
  await page.getByRole("tab", { name: "Employees", exact: true }).click();
  expect(api.reads).toHaveLength(reads);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(3);
  expect(api.reads.at(-1)?.searchParams.get("after")).toBe(periodEmployee(50).id);
  await page.getByRole("link", { name: "Payroll periods", exact: true }).last().click();
  await page.getByRole("combobox", { name: "Status", exact: true }).selectOption("CANCELLED");
  await page.getByRole("button", { name: "Apply filters", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("No payroll periods match these filters.");
});
test("selection spans pages and creation retries the same committed command", async ({ page }) => {
  const api = await installPayrollPeriodsApi(page);
  await fillCreation(page);
  await page
    .getByRole("group", { name: "Employee pages", exact: true })
    .getByRole("button", { name: "Next page", exact: true })
    .click();
  await page
    .getByRole("checkbox", { name: "Select employee: Payroll Employee 051", exact: true })
    .check();
  await page.getByRole("button", { name: "Review period", exact: true }).click();
  const heading = page.getByRole("heading", { name: "Review period", level: 1, exact: true });
  await expect(heading).toBeFocused();
  await expect(heading).toBeInViewport({ ratio: 1 });
  await expect(page.getByRole("table", { name: "Selected employees", exact: true })).toContainText(
    "Payroll Employee 051",
  );
  api.dropNext();
  await page.getByRole("button", { name: "Create draft", exact: true }).click();
  await expect(
    page.getByRole("status").filter({ hasText: "The result is unconfirmed." }),
  ).toBeVisible();
  api.failNext("payroll_period_active");
  await page.getByRole("button", { name: "Retry request", exact: true }).click();
  await expect(page.getByRole("button", { name: "Retry request", exact: true })).toBeEnabled();
  await page.getByRole("button", { name: "Retry request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Payroll draft created.");
  expect(api.writes).toHaveLength(3);
  for (const write of api.writes) expect(write).toEqual(api.writes[0]);
  expect(api.writes[0]?.body.employeeIds).toEqual([periodEmployee().id, periodEmployee(51).id]);
  await page.getByRole("link", { name: "Open period", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Employees", exact: true }).getByRole("row"),
  ).toHaveCount(3);
});
test("invalid dates stay in the form without creating a command", async ({ page }) => {
  const api = await installPayrollPeriodsApi(page);
  await fillCreation(page);
  await page.getByLabel("Planned payment date", { exact: true }).fill("2026-09-01");
  await page.getByRole("button", { name: "Review period", exact: true }).click();
  await expect(page.getByLabel("Planned payment date", { exact: true })).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  expect(api.writes).toHaveLength(0);
});
test("cancellation conflict requires protected reload and cannot cancel a changed period", async ({
  page,
}) => {
  const api = await installPayrollPeriodsApi(page);
  await page.goto(cancel);
  await page.getByLabel("Reason", { exact: true }).fill("Replace September draft");
  api.cancel();
  await page.getByRole("button", { name: "Cancel draft", exact: true }).click();
  await expect(page.getByRole("button", { name: "Reload review", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText(
    "This action requires a draft payroll period.",
  );
  await expect(page.getByRole("button", { name: "Cancel draft", exact: true })).toHaveCount(0);
  expect(api.writes).toHaveLength(1);
});
test("lost cancellation responses recover one original version and keep history", async ({
  page,
}) => {
  const api = await installPayrollPeriodsApi(page);
  await page.goto(cancel);
  await page.getByLabel("Reason", { exact: true }).fill("Replace September draft");
  api.dropNext();
  await page.getByRole("button", { name: "Cancel draft", exact: true }).click();
  await expect(
    page.getByRole("status").filter({ hasText: "The result is unconfirmed." }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Retry request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Draft cancelled.");
  expect(api.writes).toHaveLength(2);
  expect(api.writes[1]).toEqual(api.writes[0]);
  expect(api.writes[0]?.body.expectedVersion).toBe(0);
  await page.getByRole("link", { name: "Period details", exact: true }).last().click();
  await page.getByRole("tab", { name: "History", exact: true }).click();
  await expect(page.getByRole("table", { name: "History", exact: true })).toContainText(
    "Replace September draft",
  );
});
test("reading permission never reveals period preparation or cancellation controls", async ({
  page,
}) => {
  const api = await installPayrollPeriodsApi(page, ["payroll.review"]);
  await page.goto(directory);
  await expect(page.getByRole("table", { name: "Payroll periods", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Create period", exact: true })).toHaveCount(0);
  await page.goto(detail);
  await expect(page.getByRole("table", { name: "Employees", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Cancel draft", exact: true })).toHaveCount(0);
  await page.goto(creation);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.reads.some((url) => url.pathname.endsWith("/employees"))).toBe(false);
});
test("company replacement discards a pending read and obsolete employee selection", async ({
  page,
}) => {
  const api = await installPayrollPeriodsApi(page);
  await fillCreation(page);
  const held = api.holdRead();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await held.entered;
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  held.release();
  await expect(page).toHaveURL(new RegExp(`/payroll/periods\\?company=${companyIds[1]}`));
  await expect(page.getByRole("table", { name: "Selected employees", exact: true })).toHaveCount(0);
  await expect(page.getByRole("status")).toContainText("No payroll periods match these filters.");
  expect(api.writes).toHaveLength(0);
});
test("locale and theme keep creation input and selection on a narrow viewport", async ({
  page,
}) => {
  const api = await installPayrollPeriodsApi(page);
  await fillCreation(page);
  await page.getByLabel("Search name or employee number", { exact: true }).fill("001");
  await page.getByRole("button", { name: "Search", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Employees", exact: true }).getByRole("row"),
  ).toHaveCount(2);
  const reads = api.reads.length;
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue("Prepare October payroll");
  await expect(page.getByRole("table", { name: "Karyawan terpilih", exact: true })).toContainText(
    "Payroll Employee 001",
  );
  expect(api.reads).toHaveLength(reads);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-payroll-period-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Tinjau periode", exact: true }).click();
  const heading = page.getByRole("heading", { name: "Tinjau periode", level: 1, exact: true });
  await expect(heading).toBeFocused();
  await expect(heading).toBeInViewport({ ratio: 1 });
  await page.screenshot({
    path: "../../.work/dashboard-payroll-period-narrow-review.png",
    fullPage: true,
    animations: "disabled",
  });
});
