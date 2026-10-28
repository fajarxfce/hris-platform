import { expect, type Page, test } from "@playwright/test";
import { companyIds, installIdentityApi } from "./identity-api";

const definitions = {
  HEADCOUNT: "headcount.v1",
  ATTENDANCE_EXCEPTIONS: "attendance-exceptions.v1",
  ATTENDANCE_CLOSINGS: "attendance-closings.v1",
  LEAVE: "leave.v1",
  OVERTIME: "overtime.v1",
  EXPENSES: "expenses.v1",
  PAYROLL_COSTS: "payroll-costs.v1",
};
const permissions = [
  "company.read",
  "reports.read",
  "people.read",
  "workforce.read",
  "leave.read",
  "expenses.read",
  "payroll.read",
];
async function install(page: Page, grants = permissions) {
  const identity = await installIdentityApi(page, { signedIn: true, permissions: grants });
  const reads: string[] = [];
  let failure = false;
  let kinds = Object.keys(definitions);
  await page.route("**/reports/catalogue", async (route) => {
    const companyId = new URL(route.request().url()).pathname.split("/")[4];
    reads.push(companyId ?? "");
    if (failure)
      return route.fulfill({
        status: 500,
        json: { code: "database_failure", detail: "PRIVATE DIAGNOSTIC" },
      });
    return route.fulfill({
      json: {
        companyId,
        evaluatedAt: "2026-10-01T00:00:00Z",
        reports: kinds.map((kind) => ({
          kind,
          definitionVersion: definitions[kind as keyof typeof definitions],
        })),
      },
    });
  });
  return {
    identity,
    reads,
    fail: (value: boolean) => {
      failure = value;
    },
    select: (value: string[]) => {
      kinds = value;
    },
  };
}

test("catalogue opens an authorized report and browser return retains catalogue discovery", async ({
  page,
}) => {
  const api = await install(page);
  await page.route("**/api/v1/reports/headcount?*", async (route) => {
    const url = new URL(route.request().url());
    const counts = {
      employments: 0,
      persons: 0,
      active: 0,
      probation: 0,
      suspended: 0,
      permanent: 0,
      fixedTerm: 0,
    };
    await route.fulfill({
      json: {
        asOf: url.searchParams.get("asOf"),
        evaluatedAt: "2026-10-01T00:00:00Z",
        definitionVersion: "headcount.v1",
        totals: counts,
        companies: [{ companyId: companyIds[0], counts }],
      },
    });
  });
  await page.goto("/reports");
  const table = page.getByRole("table", { name: "Available reports", exact: true });
  await expect(table.getByRole("row")).toHaveCount(8);
  await table.getByRole("button", { name: "Open report: Headcount", exact: true }).click();
  await expect(page).toHaveURL(/\/reports\/headcount/u);
  await expect(page.getByRole("region", { name: "Employment records", exact: true })).toContainText(
    "0",
  );
  await page.goBack();
  await expect(table.getByRole("row")).toHaveCount(8);
  expect(api.identity.unhandled).toEqual([]);
});

test("catalogue clears stale rows on failure and refresh and explains empty authorized scope", async ({
  page,
}) => {
  const api = await install(page);
  await page.goto("/reports");
  const table = page.getByRole("table", { name: "Available reports", exact: true });
  await expect(table).toBeVisible();
  api.fail(true);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(table).toHaveCount(0);
  await expect(page.locator("body")).not.toContainText("PRIVATE DIAGNOSTIC");
  api.fail(false);
  api.select([]);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(
    page.getByText("No reports are available with your current company permissions.", {
      exact: true,
    }),
  ).toBeVisible();
  await expect(table).toHaveCount(0);
  api.select(["LEAVE"]);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(2);
  expect(api.identity.unhandled).toEqual([]);
});

test("catalogue denies source-only access before I/O and presents Indonesian dark narrow layouts", async ({
  page,
  context,
}) => {
  const denied = await context.newPage();
  const restricted = await install(denied, ["company.read", "people.read"]);
  await denied.goto("/reports");
  await expect(denied.getByRole("alert")).toBeVisible();
  expect(restricted.reads).toEqual([]);
  await expect(denied.getByRole("link", { name: "Report catalogue", exact: true })).toHaveCount(0);
  await denied.close();
  const api = await install(page);
  await page.goto("/reports");
  await expect(page.getByRole("table", { name: "Available reports", exact: true })).toBeVisible();
  await page.getByRole("combobox", { name: "Language" }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  const table = page.getByRole("table", { name: "Laporan tersedia", exact: true });
  await expect(table.getByRole("row")).toHaveCount(8);
  const button = table.getByRole("button", { name: "Buka laporan: Jumlah karyawan", exact: true });
  await expect(button).toBeVisible();
  expect(
    await table.evaluate((element) => {
      const rect = element.getBoundingClientRect();
      const parent = element.closest(".app-table-scroll")?.getBoundingClientRect();
      return parent !== undefined && rect.width <= parent.width + 1;
    }),
  ).toBe(true);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({
    path: "../../.work/report-catalogue-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(api.identity.unhandled).toEqual([]);
});

test("company replacement cancels pending catalogue reads and admits only the new company", async ({
  page,
}) => {
  const api = await install(page);
  let release!: () => void;
  let entered!: () => void;
  const waiting = new Promise<void>((resolve) => {
    entered = resolve;
  });
  const held = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route(`**/companies/${companyIds[0]}/reports/catalogue`, async (route) => {
    entered();
    await held;
    await route.fulfill({
      json: {
        companyId: companyIds[0],
        evaluatedAt: "2026-10-01T00:00:00Z",
        reports: [{ kind: "PAYROLL_COSTS", definitionVersion: "payroll-costs.v1" }],
      },
    });
  });
  await page.goto("/reports");
  try {
    await waiting;
    api.select(["HEADCOUNT"]);
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    const table = page.getByRole("table", { name: "Available reports", exact: true });
    await expect(table.getByRole("row")).toHaveCount(2);
    release();
    await expect(table).toContainText("Headcount");
    await expect(table).not.toContainText("Payroll cost");
    expect(api.reads).toEqual([companyIds[1]]);
    expect(api.identity.unhandled).toEqual([]);
  } finally {
    release();
  }
});
