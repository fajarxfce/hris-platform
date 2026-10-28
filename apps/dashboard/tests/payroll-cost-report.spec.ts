import { expect, type Page, test } from "@playwright/test";
import { companyIds, installIdentityApi } from "./identity-api";

const permissions = ["company.read", "payroll.read", "reports.read"];
const path = "/reports/payroll-costs?from=2026-11&until=2026-12";

function report(companyId: string, from: string, until: string, employments = 1) {
  const year = Number(from.slice(0, 4)),
    first = Number(from.slice(5)) - 1;
  const length = (Number(until.slice(0, 4)) - year) * 12 + Number(until.slice(5)) - first;
  const money = (minor: bigint) =>
    `${minor < 0n ? "-" : ""}${(minor < 0n ? -minor : minor) / 100n}.${((minor < 0n ? -minor : minor) % 100n).toString().padStart(2, "0")}`;
  const counts = (n: number) => ({
    assessments: n,
    employments: n,
    revisedAssessments: n,
    employerCost: money(10003n * BigInt(n)),
    taxableGross: money(10002n * BigInt(n)),
    employeeDeductions: money(BigInt(n)),
    withheld: money(-BigInt(n)),
    takeHome: money(10003n * BigInt(n)),
  });
  return {
    companyId,
    from,
    until,
    evaluatedAt: "2027-01-01T00:00:00Z",
    currency: "IDR",
    definitionVersion: "payroll-costs.v1",
    totals: { ...counts(length * employments), employments },
    months: Array.from({ length }, (_, i) => ({
      month: new Date(Date.UTC(year, first + i, 1)).toISOString().slice(0, 7),
      counts: counts(employments),
    })),
  };
}

async function installReports(page: Page) {
  const requests: string[] = [];
  let failing = false;
  await page.route("**/api/v1/companies/*/reports/payroll-costs?*", async (route) => {
    const url = new URL(route.request().url());
    requests.push(url.pathname + url.search);
    expect(route.request().headers()["x-hris-client-platform"]).toBe("WEB");
    if (failing)
      return route.fulfill({
        status: 503,
        json: {
          code: "service_unavailable",
          fields: {},
          parameters: {},
          detail: "PRIVATE REPORT DETAIL",
        },
      });
    return route.fulfill({
      json: report(
        url.pathname.split("/")[4] ?? "",
        url.searchParams.get("from") ?? "",
        url.searchParams.get("until") ?? "",
      ),
    });
  });
  return {
    requests,
    fail: (next: boolean) => {
      failing = next;
    },
  };
}

test("payroll costs retain applied earnings months, exact refunds and browser return from the payslip month", async ({
  page,
}) => {
  const identity = await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installReports(page);
  const drilldowns: string[] = [];
  await page.route("**/api/v1/companies/*/payroll/payslips?*", (route) => {
    drilldowns.push(route.request().url());
    return route.fulfill({ json: { items: [], nextCursor: null } });
  });
  await page.goto(path);
  await expect(
    page.getByRole("heading", { name: "Payroll cost report", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("region", { name: "Published payslips", exact: true })).toContainText(
    "2",
  );
  await expect(page.getByRole("region", { name: "Employment records", exact: true })).toContainText(
    "1",
  );
  await expect(
    page.getByRole("region", { name: "Tax withheld / refunded", exact: true }),
  ).toContainText("-IDR 0.02");
  const reads = api.requests.length;
  await page.getByLabel("From month", { exact: true }).fill("2026-12");
  expect(api.requests).toHaveLength(reads);
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page.getByRole("region", { name: "Published payslips", exact: true })).toContainText(
    "1",
  );
  await expect(
    page.getByRole("region", { name: "Tax withheld / refunded", exact: true }),
  ).toContainText("-IDR 0.01");
  await page.goBack();
  await expect(page.getByLabel("From month", { exact: true })).toHaveValue("2026-11");
  await expect(page.getByRole("region", { name: "Published payslips", exact: true })).toContainText(
    "2",
  );
  await page.goForward();
  await expect(page.getByLabel("From month", { exact: true })).toHaveValue("2026-12");
  await expect(page.getByRole("region", { name: "Published payslips", exact: true })).toContainText(
    "1",
  );
  await page.getByRole("combobox", { name: "Language" }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(
    page.getByRole("region", { name: "Pajak dipotong / dikembalikan", exact: true }),
  ).toContainText("-Rp 0,01");
  const action = page.getByRole("button", { name: /^Lihat slip gaji:/u });
  await expect
    .poll(() =>
      action.evaluate((element) => {
        const bounds = element.getBoundingClientRect(),
          container = element.closest(".app-table-scroll")?.getBoundingClientRect();
        return (
          container !== undefined &&
          bounds.left >= container.left &&
          bounds.right <= container.right
        );
      }),
    )
    .toBe(true);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({
    path: "../../.work/dashboard-payroll-cost-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  await action.click();
  await expect(page).toHaveURL(
    /\/payroll\/payslips\?company=.*&from=2026-12&until=2026-12&scope=AUTHORIZED/u,
  );
  await expect.poll(() => drilldowns.length).toBe(1);
  expect(Object.fromEntries(new URL(drilldowns[0] ?? "").searchParams)).toEqual({
    from: "2026-12",
    until: "2026-12",
    scope: "AUTHORIZED",
    limit: "50",
    query: "",
  });
  await page.goBack();
  await expect(
    page.getByRole("heading", { name: "Laporan biaya payroll", exact: true }),
  ).toBeVisible();
  await expect(page.getByLabel("Dari bulan", { exact: true })).toHaveValue("2026-12");
  await expect(page.getByRole("region", { name: "Slip gaji terbit", exact: true })).toContainText(
    "1",
  );
  expect(identity.unhandled).toEqual([]);
});
test("obsolete company reports cannot repopulate the new workspace and logout removes counts", async ({
  page,
}) => {
  const identity = await installIdentityApi(page, { signedIn: true, permissions });
  let enter!: () => void;
  let release!: () => void;
  const entered = new Promise<void>((resolve) => {
    enter = resolve;
  });
  const held = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route("**/api/v1/companies/*/reports/payroll-costs?*", async (route) => {
    const url = new URL(route.request().url());
    const company = url.pathname.split("/")[4] ?? "";
    if (company === companyIds[0]) {
      enter();
      await held;
    }
    await route.fulfill({
      json: report(
        company,
        url.searchParams.get("from") ?? "",
        url.searchParams.get("until") ?? "",
        company === companyIds[0] ? 9 : 1,
      ),
    });
  });
  try {
    await page.goto(path);
    await entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(
      page.getByRole("region", { name: "Employment records", exact: true }),
    ).toContainText("1");
    release();
    await expect(
      page.getByRole("region", { name: "Employment records", exact: true }),
    ).not.toContainText("9");
    await expect(page.getByRole("main")).not.toContainText("North Company");
    await page.getByRole("button", { name: "Sign out", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Sign in", exact: true })).toBeVisible();
    await expect(page.getByRole("region", { name: "Employment records", exact: true })).toHaveCount(
      0,
    );
    expect(identity.unhandled).toEqual([]);
  } finally {
    release();
  }
});

test("failed refresh clears counts and an explicit retry recovers without exposing server diagnostics", async ({
  page,
}) => {
  const identity = await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installReports(page);
  await page.goto(path);
  await expect(page.getByRole("region", { name: "Employment records", exact: true })).toBeVisible();
  api.fail(true);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("region", { name: "Employment records", exact: true })).toHaveCount(
    0,
  );
  await expect(page.getByRole("main")).not.toContainText("PRIVATE REPORT DETAIL");
  const failedRequests = api.requests.length;
  api.fail(false);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("region", { name: "Employment records", exact: true })).toContainText(
    "1",
  );
  expect(api.requests).toHaveLength(failedRequests + 1);
  expect(identity.unhandled).toEqual([]);
});

test("invalid month ranges and missing company payroll scope issue no report requests", async ({
  page,
}) => {
  const identity = await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installReports(page);
  await page.goto("/reports/payroll-costs?from=2026-01&until=2027-01");
  await expect(page.getByRole("main")).toContainText(
    "Choose one to twelve earnings months between 2024 and 2100",
  );
  expect(api.requests).toHaveLength(0);
  identity.setPermissions([
    "company.read",
    "reports.read",
    "payroll.self.read",
    "payroll.compensation.manage",
  ]);
  await page.goto(path);
  await expect(page.getByRole("link", { name: "Payroll cost report", exact: true })).toHaveCount(0);
  await expect(page.getByRole("region", { name: "Published payslips", exact: true })).toHaveCount(
    0,
  );
  expect(api.requests).toHaveLength(0);
  expect(identity.unhandled).toEqual([]);
});

test("empty published months retain zero totals and disable drill-down", async ({ page }) => {
  const identity = await installIdentityApi(page, { signedIn: true, permissions });
  await page.route("**/api/v1/companies/*/reports/payroll-costs?*", (route) =>
    route.fulfill({ json: report(companyIds[0], "2026-11", "2026-12", 0) }),
  );
  await page.goto(path);
  await expect(page.getByRole("region", { name: "Published payslips", exact: true })).toContainText(
    "0",
  );
  const rows = page.getByRole("table", { name: "By earnings month", exact: true });
  await expect(rows.getByRole("row")).toHaveCount(3);
  await expect(rows.getByRole("button", { name: /^View payslips:/u }).first()).toBeDisabled();
  await expect(rows.getByRole("button", { name: /^View payslips:/u }).last()).toBeDisabled();
  expect(identity.unhandled).toEqual([]);
});
