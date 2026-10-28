import { expect, test } from "@playwright/test";
import { installExpenseReports } from "./expense-report-api";
import { companyIds, installIdentityApi } from "./identity-api";

const permissions = ["company.read", "reports.read", "expenses.read"];
const path = "/reports/expenses?from=2024-01-01&until=2024-12-31";

test("annual expense totals preserve applied filters and exact localized money, with narrow-screen status drill-down", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installExpenseReports(page);
  await page.goto(path);
  await expect(page.getByRole("heading", { name: "Expense report", exact: true })).toBeVisible();
  await expect(page.getByRole("region", { name: "Amount in period", exact: true })).toContainText(
    "7,650,038.25",
  );
  await expect(page.getByRole("region", { name: "Employment records", exact: true })).toContainText(
    "1",
  );
  const reads = api.reads.length;
  await page.getByLabel("From date", { exact: true }).fill("2024-01-02");
  expect(api.reads).toHaveLength(reads);
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect.poll(() => api.reads.at(-1)?.searchParams.get("from")).toBe("2024-01-02");
  await page.goBack();
  await expect(page.getByLabel("From date", { exact: true })).toHaveValue("2024-01-01");
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(
    page.getByRole("region", { name: "Nominal dalam periode", exact: true }),
  ).toContainText("7.650.038,25");
  const open = page.getByRole("button", { name: "Lihat klaim: Menunggu", exact: true });
  await expect(open).toBeVisible();
  expect(
    await open.evaluate((element) => {
      const rect = element.getBoundingClientRect();
      return rect.left >= 0 && rect.right <= window.innerWidth;
    }),
  ).toBe(true);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({
    path: "../../.work/dashboard-expense-report-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  await open.click();
  await expect(
    page.getByRole("heading", { name: "Klaim dalam laporan biaya", exact: true }),
  ).toBeVisible();
  const rows = page.getByRole("table", { name: "Klaim", exact: true });
  await expect(rows.getByRole("row")).toHaveCount(51);
  expect(new URL(page.url()).searchParams.get("status")).toBe("PENDING");
  expect(new URL(page.url()).searchParams.get("company")).toBe(companyIds[0]);
  await page.getByRole("button", { name: "Halaman berikutnya", exact: true }).click();
  await expect(rows.getByRole("row")).toHaveCount(2);
  await expect(rows).toContainText("Report employee 51");
  await page.getByRole("button", { name: "Halaman pertama", exact: true }).click();
  await expect(rows.getByRole("row")).toHaveCount(51);
  await page.getByRole("link", { name: "Kembali ke laporan", exact: true }).click();
  await expect(page.getByLabel("Dari tanggal", { exact: true })).toHaveValue("2024-01-01");
});

test("failed refreshes clear both totals and private request rows until explicit retry succeeds", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installExpenseReports(page);
  await page.goto(path);
  await expect(page.getByRole("region", { name: "Amount in period", exact: true })).toBeVisible();
  api.fail(true);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("region", { name: "Amount in period", exact: true })).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText("PRIVATE EXPENSE REPORT");
  api.fail(false);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await page.getByRole("button", { name: "View claims: Pending", exact: true }).click();
  await expect(page.getByRole("main")).toContainText("Report employee 1");
  api.fail(true);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("main")).not.toContainText("Report employee");
  api.fail(false);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("main")).toContainText("Report employee 1");
});

test("company replacement resets report filters and removes the preceding company's rows", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installExpenseReports(page);
  await page.goto(`/reports/expenses/claims?from=2024-01-01&until=2024-12-31&status=PENDING`);
  await expect(page.getByRole("main")).toContainText("Report employee 1");
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  await expect(page.getByRole("status")).toContainText(
    "No submitted transactions match this period and status.",
  );
  await expect(page.getByRole("main")).not.toContainText("Report employee");
  expect(new URL(page.url()).searchParams.has("status")).toBe(false);
  expect(api.reads.at(-1)?.searchParams.get("from")).not.toBe("2024-01-01");
});

test("source grants and invalid annual windows prevent report acquisition", async ({ page }) => {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    permissions: ["company.read", "reports.read"],
  });
  const api = await installExpenseReports(page);
  await page.goto(path);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.reads).toHaveLength(0);
  await expect(page.getByRole("link", { name: "Expense report", exact: true })).toHaveCount(0);
  identity.setPermissions(permissions);
  await page.reload();
  await expect(page.getByRole("heading", { name: "Expense report", exact: true })).toBeVisible();
  await expect(page.getByRole("region", { name: "Amount in period", exact: true })).toBeVisible();
  const count = api.reads.length;
  await page.getByLabel("From date", { exact: true }).fill("2023-12-31");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page.getByRole("main")).toContainText("Choose a date range of up to 366 days");
  expect(api.reads).toHaveLength(count);
});

test("an unavailable exact submission still returns to the selected report page and status", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true, permissions });
  await installExpenseReports(page);
  const submission = "10000000-0000-4000-8000-000000000051";
  const reads: string[] = [];
  await page.route("**/api/v1/companies/*/expenses/submissions/*", async (route) => {
    reads.push(new URL(route.request().url()).pathname);
    return route.fulfill({ status: 404, json: { code: "expense_submission_not_found" } });
  });
  await page.goto("/reports/expenses/claims?from=2024-01-01&until=2024-12-31&status=PENDING");
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(page.getByRole("table", { name: "Claims", exact: true })).toContainText(
    "Report employee 51",
  );
  const selection = new URL(page.url()).searchParams.toString();
  await page
    .getByRole("button", {
      name: "View submission: Report employee 51 · Travel 51 · 1",
      exact: true,
    })
    .click();
  expect(new URL(page.url()).pathname).toBe(`/expenses/submissions/${submission}`);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(reads).toEqual([`/api/v1/companies/${companyIds[0]}/expenses/submissions/${submission}`]);
  await page.getByRole("link", { name: "Back to report", exact: true }).click();
  expect(new URL(page.url()).searchParams.toString()).toBe(selection);
  await expect(
    page.getByRole("table", { name: "Claims", exact: true }).getByRole("row"),
  ).toHaveCount(2);
  await page.getByRole("button", { name: "First page", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Claims", exact: true }).getByRole("row"),
  ).toHaveCount(51);
});
