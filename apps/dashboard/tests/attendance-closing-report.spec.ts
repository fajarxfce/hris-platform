import { expect, test } from "@playwright/test";
import { installClosingReports } from "./attendance-closing-report-api";
import { companyIds } from "./identity-api";

const path = "/reports/attendance-closings?from=2026-09&until=2026-10";
test("closing months compare original and current facts and open the exact revision with report return", async ({
  page,
}) => {
  const api = await installClosingReports(page);
  await page.goto(path);
  const table = page.getByRole("table", { name: "Attendance facts", exact: true });
  await expect(table.getByRole("row").filter({ hasText: "Recorded absence" })).toContainText("1");
  await expect(table.getByRole("row").filter({ hasText: "Recorded absence" })).toContainText("2");
  const months = page.getByRole("table", { name: "Published months", exact: true });
  await expect(
    months.getByRole("button", { name: "View employees: October 2026", exact: true }),
  ).toBeDisabled();
  await months.getByRole("button", { name: "View employees: September 2026", exact: true }).click();
  await expect(page).toHaveURL(/\/reports\/attendance-closings\/employees\?/u);
  await page.getByRole("button", { name: /^View selected snapshot:/u }).click();
  await expect(page).toHaveURL(new RegExp(`/adjustments/${api.revised.summary.id}\\?`));
  await expect(
    page.getByRole("table", { name: "Retained monthly attendance", exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Original closing", exact: true }).click();
  await expect(page.getByRole("table", { name: "Daily results", exact: true })).toBeVisible();
  await page.getByRole("link", { name: "Back to report", exact: true }).click();
  await expect(page.getByRole("table", { name: "Closing employees", exact: true })).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Closing employees", level: 1, exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Back to report", exact: true }).click();
  await expect(page.getByLabel("From month", { exact: true })).toHaveValue("2026-09");
  await expect(page.getByLabel("Until month", { exact: true })).toHaveValue("2026-10");
  const reads = api.reads.length;
  await page.getByLabel("From month", { exact: true }).fill("2026-10");
  expect(api.reads).toHaveLength(reads);
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page.getByRole("region", { name: "Closed months", exact: true })).toContainText("0");
  await page.goBack();
  await expect(table).toBeVisible();
  expect(api.identity.unhandled).toEqual([]);
});
test("closing report recovers explicit failures and keeps month actions visible in Indonesian dark mobile layout", async ({
  page,
}) => {
  const api = await installClosingReports(page);
  await page.goto(path);
  await expect(page.getByRole("table", { name: "Attendance facts", exact: true })).toBeVisible();
  api.fail(true);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("table", { name: "Attendance facts", exact: true })).toHaveCount(0);
  await expect(page.locator("body")).not.toContainText("PRIVATE REPORT");
  api.fail(false);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("table", { name: "Attendance facts", exact: true })).toBeVisible();
  await page.getByRole("combobox", { name: "Language" }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  const action = page.getByRole("button", { name: "Lihat karyawan: September 2026", exact: true });
  await expect(action).toBeVisible();
  expect(
    await action.evaluate((element) => {
      const rect = element.getBoundingClientRect(),
        parent = element.closest(".app-table-scroll")?.getBoundingClientRect();
      return parent !== undefined && rect.left >= parent.left && rect.right <= parent.right;
    }),
  ).toBe(true);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  const comparison = page.getByRole("table", { name: "Fakta presensi", exact: true });
  expect(
    await comparison.evaluate((element) => {
      const rect = element.getBoundingClientRect(),
        parent = element.closest(".app-table-scroll")?.getBoundingClientRect();
      return parent !== undefined && rect.width <= parent.width + 1;
    }),
  ).toBe(true);
  await page.screenshot({
    path: "../../.work/attendance-closing-report-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(api.identity.unhandled).toEqual([]);
});
test("closing report rejects bad ranges and source-only authority and resets company filters", async ({
  page,
}) => {
  const api = await installClosingReports(page);
  await page.goto(path);
  await expect(page.getByRole("table", { name: "Attendance facts", exact: true })).toBeVisible();
  const reads = api.reads.length;
  await page.getByLabel("Until month", { exact: true }).fill("2027-09");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  expect(api.reads).toHaveLength(reads);
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  await expect.poll(() => new URL(page.url()).searchParams.get("company")).toBe(companyIds[1]);
  expect(new URL(page.url()).searchParams.get("from")).toMatch(/-01$/u);
  const denied = await page.context().newPage();
  const api2 = await installClosingReports(denied, ["company.read", "reports.read"]);
  await denied.goto(path);
  await expect(denied.getByRole("alert")).toBeVisible();
  expect(api2.reads).toEqual([]);
  await denied.close();
});
test("employee continuation survives an unavailable exact snapshot and preserves the report window", async ({
  page,
}) => {
  const api = await installClosingReports(page);
  api.many();
  await page.goto(
    `/reports/attendance-closings/employees?company=${companyIds[0]}&month=2026-09&from=2026-01&until=2026-12`,
  );
  const table = page.getByRole("table", { name: "Closing employees", exact: true });
  await expect(table.getByRole("row")).toHaveCount(51);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(2);
  const current = page.url();
  await page.getByRole("button", { name: /^View selected snapshot:/u }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await page.getByRole("link", { name: "Back to report", exact: true }).click();
  await expect(page).toHaveURL(current);
  await page.getByRole("button", { name: "First page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(51);
  expect(api.identity.unhandled).toEqual([]);
});
