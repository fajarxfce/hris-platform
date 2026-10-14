import { expect, type Page, test } from "@playwright/test";
import {
  calculationCompany,
  calculationId,
  calculationPeriod,
} from "./fixtures/payroll-calculations";
import { installPayrollCalculationsApi } from "./payroll-calculations-api";

const overview = `/payroll/periods/${calculationPeriod}/calculations?company=${calculationCompany}`,
  start = `/payroll/periods/${calculationPeriod}/calculate?company=${calculationCompany}`,
  run = `/payroll/runs/${calculationId}?company=${calculationCompany}`;
async function fillStart(page: Page) {
  await page.goto(start);
  await page.getByLabel("Income due date", { exact: true }).fill("2026-09-30");
  await page.getByLabel("Review reference", { exact: true }).fill("Reviewed salary liabilities");
  await page.getByLabel("Reason", { exact: true }).fill("Calculate September payroll");
}
test("period navigation opens bounded calculation results and fetches one employee only when opened", async ({
  page,
}) => {
  const api = await installPayrollCalculationsApi(page, { employees: 52, failed: 1 });
  await page.goto(`/payroll/periods/${calculationPeriod}?company=${calculationCompany}`);
  await page.getByRole("main").getByRole("link", { name: "Calculations", exact: true }).click();
  await page.getByRole("button", { name: "View run: Calculation run 1", exact: true }).click();
  const table = page.getByRole("table", { name: "Employee results", exact: true });
  await expect(table.getByRole("row")).toHaveCount(51);
  await expect(table).toContainText("An independent reviewer must verify the monthly input.");
  expect(
    api.reads.filter(
      (url) => url.pathname.includes("/runs/") && url.pathname.includes("/employees/"),
    ),
  ).toHaveLength(0);
  const reads = api.reads.length;
  await page.getByRole("tab", { name: "Job attempts", exact: true }).click();
  await expect(page.getByRole("table", { name: "Job attempts", exact: true })).toBeVisible();
  await page.getByRole("tab", { name: "Source evidence", exact: true }).click();
  expect(api.reads).toHaveLength(reads);
  await page.getByRole("tab", { name: "Employee results", exact: true }).click();
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(3);
  expect(api.reads.at(-1)?.searchParams.get("after")).toBe("50");
  await page
    .getByRole("button", { name: "View result: Calculation Employee 051", exact: true })
    .click();
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText(
    "Calculation Employee 051",
  );
  await expect(page.getByRole("region", { name: "Income tax", exact: true })).toContainText("-IDR");
  await page.getByRole("tab", { name: "Source evidence", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Attendance evidence", exact: true }).getByRole("row"),
  ).toHaveCount(31);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({
    path: "../../.work/dashboard-payroll-calculation-employee-narrow.png",
    fullPage: true,
  });
  await page.getByRole("link", { name: "View run", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(3);
  expect(api.reads.at(-1)?.searchParams.get("after")).toBe("50");
});
test("a lost start response retains one command across retries and restores progress from its receipt", async ({
  page,
}) => {
  const api = await installPayrollCalculationsApi(page, { initial: "draft" });
  await fillStart(page);
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByLabel("Tanggal penghasilan terutang", { exact: true })).toHaveValue(
    "2026-09-30",
  );
  await page.getByRole("combobox", { name: "Bahasa", exact: true }).selectOption("en");
  api.dropNext();
  await page.getByRole("button", { name: "Start calculation", exact: true }).click();
  await expect(
    page.getByRole("status").filter({ hasText: "The result is unconfirmed." }),
  ).toBeVisible();
  api.failNext("stale_policy_version", 409);
  await page.getByRole("button", { name: "Retry request", exact: true }).click();
  await expect(page.getByRole("button", { name: "Retry request", exact: true })).toBeEnabled();
  await page.getByRole("button", { name: "Retry request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Calculation queued.");
  expect(api.writes).toHaveLength(3);
  expect(api.writes[1]).toEqual(api.writes[0]);
  expect(api.writes[2]).toEqual(api.writes[0]);
  expect(api.records.size).toBe(1);
  await page.getByRole("link", { name: "View run", exact: true }).click();
  await expect(
    page.getByRole("region", { name: "Calculation progress", exact: true }),
  ).toContainText("Running");
  const id = String(api.writes[0]?.body.id);
  api.complete(id);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("table", { name: "Employee results", exact: true })).toContainText(
    "Calculation Employee 001",
  );
});
test("invalid dates never submit and an observed policy conflict requires an explicit new review", async ({
  page,
}) => {
  const api = await installPayrollCalculationsApi(page, { initial: "draft" });
  await fillStart(page);
  await page.getByLabel("Income due date", { exact: true }).fill("2026-08-31");
  await page.getByRole("button", { name: "Start calculation", exact: true }).click();
  await expect(page.getByLabel("Income due date", { exact: true })).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  expect(api.writes).toHaveLength(0);
  await page.getByLabel("Income due date", { exact: true }).fill("2026-09-30");
  api.changePolicy();
  await page.getByRole("button", { name: "Start calculation", exact: true }).click();
  await expect(page.getByRole("button", { name: "Reload review", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue(
    "Calculate September payroll",
  );
  await page.getByRole("button", { name: "Start calculation", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Calculation queued.");
  expect(api.writes[0]?.body.expectedPolicyVersion).toBe(0);
  expect(api.writes[1]?.body.expectedPolicyVersion).toBe(1);
  expect(api.writes[1]?.operation).not.toBe(api.writes[0]?.operation);
});
test("resume keeps original results and shows the replacement attempt without restarting the run", async ({
  page,
}) => {
  const api = await installPayrollCalculationsApi(page, { initial: "stopped" });
  await page.goto(run);
  await page.getByRole("link", { name: "Resume calculation", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Continue reviewed payroll");
  api.dropNext();
  await page.getByRole("button", { name: "Resume calculation", exact: true }).click();
  await expect(page.getByRole("button", { name: "Retry request", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Retry request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Calculation resumed.");
  expect(api.writes[1]).toEqual(api.writes[0]);
  await page.getByRole("status").getByRole("link", { name: "View run", exact: true }).click();
  await page.getByRole("tab", { name: "Job attempts", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Job attempts", exact: true }).getByRole("row"),
  ).toHaveCount(3);
  expect(api.records.size).toBe(1);
});
test("abandon recovers its original receipt and leaves historical outcomes readable", async ({
  page,
}) => {
  const api = await installPayrollCalculationsApi(page);
  await page.goto(run);
  await page.getByRole("link", { name: "Abandon run", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Correct the verified input");
  api.dropNext();
  await page.getByRole("button", { name: "Abandon run", exact: true }).click();
  await expect(page.getByRole("button", { name: "Retry request", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Retry request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Run abandoned.");
  expect(api.writes[1]).toEqual(api.writes[0]);
  expect(api.review.period.status).toBe("DRAFT");
  await page.getByRole("status").getByRole("link", { name: "View run", exact: true }).click();
  await expect(page.getByRole("table", { name: "Employee results", exact: true })).toContainText(
    "Calculation Employee 001",
  );
  await expect(page.getByRole("link", { name: "Abandon run", exact: true })).toHaveCount(0);
  await page.getByRole("link", { name: "Calculations", exact: true }).click();
  await expect(page.getByRole("link", { name: "Start calculation", exact: true })).toBeVisible();
});
test("read-only grants never expose start or recovery commands", async ({ page }) => {
  const api = await installPayrollCalculationsApi(page, { permissions: ["payroll.review"] });
  await page.goto(overview);
  await expect(page.getByRole("table", { name: "Run history", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Start calculation", exact: true })).toHaveCount(0);
  await page.goto(run);
  await expect(page.getByRole("link", { name: "Abandon run", exact: true })).toHaveCount(0);
  await page.goto(start);
  await expect(page.getByRole("alert")).toHaveText("You do not have access to this resource.");
  await expect(page.getByLabel("Income due date", { exact: true })).toHaveCount(0);
  expect(api.writes).toHaveLength(0);
});
test("company change cancels an old read and cannot release previous employee data", async ({
  page,
}) => {
  const api = await installPayrollCalculationsApi(page);
  await page.goto(run);
  await expect(page.getByRole("table", { name: "Employee results", exact: true })).toBeVisible();
  const held = api.holdNext();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await held.entered.promise;
  try {
    await page
      .getByRole("combobox", { name: "Company", exact: true })
      .selectOption("10000000-0000-4000-8000-000000000002");
    await expect(page.getByRole("heading", { name: "Payroll periods", exact: true })).toBeVisible();
  } finally {
    held.released.resolve();
  }
  await expect(page.getByText("Calculation Employee 001", { exact: true })).toHaveCount(0);
  expect(api.writes).toHaveLength(0);
});
test("a denied progress refresh withdraws results and layout remains bounded in dark mode", async ({
  page,
}) => {
  const api = await installPayrollCalculationsApi(page);
  await page.goto(run);
  await expect(page.getByRole("table", { name: "Employee results", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({
    path: "../../.work/dashboard-payroll-calculation-run-narrow.png",
    fullPage: true,
  });
  api.failNext("access_denied");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("table", { name: "Employee results", exact: true })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Abandon run", exact: true })).toHaveCount(0);
  expect(await page.content()).not.toContain("PRIVATE TECHNICAL DIAGNOSTIC");
});
