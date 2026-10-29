import { expect, type Page, test } from "@playwright/test";
import { calculationId } from "./fixtures/payroll-calculations";
import {
  comparisonBaseline,
  comparisonFixture,
  comparisonManyFixture,
  comparisonPeriod,
} from "./fixtures/payroll-comparison";
import { periodRecord } from "./fixtures/payroll-periods";
import { companyIds } from "./identity-api";
import { installPayrollCalculationsApi } from "./payroll-calculations-api";

const path = `/payroll/runs/${calculationId}/comparison`;
async function install(page: Page, many = false) {
  const api = await installPayrollCalculationsApi(page, { employees: many ? 51 : 3, failed: 0 });
  const reads: URL[] = [];
  let broken = false;
  let denied = false;
  let held: { entered: () => void; wait: Promise<void> } | null = null;
  await page.route("**/api/v1/companies/*/payroll/periods?*", async (route) => {
    const url = new URL(route.request().url());
    return route.fulfill({
      json: {
        items:
          url.pathname.includes(companyIds[0] as string) &&
          url.searchParams.get("from") === "2026-08"
            ? [
                periodRecord({
                  id: comparisonPeriod,
                  earningsMonth: "2026-08",
                  plannedPaymentMonth: "2026-09",
                  plannedPaymentDate: "2026-09-02",
                  participantCount: 3,
                  status: "CALCULATED",
                  version: 2,
                  currentRunId: comparisonBaseline,
                }),
              ]
            : [],
        nextCursor: null,
      },
    });
  });
  await page.route("**/api/v1/companies/*/payroll/runs/*/comparison?*", async (route) => {
    expect(route.request().method()).toBe("GET");
    const url = new URL(route.request().url());
    reads.push(url);
    const wait = held;
    held = null;
    if (wait) {
      wait.entered();
      await wait.wait;
    }
    if (denied) return route.fulfill({ status: 403, json: { code: "access_denied" } });
    const data = many ? comparisonManyFixture() : comparisonFixture();
    data.changedOnly = url.searchParams.get("changedOnly") === "true";
    if (data.changedOnly)
      data.employees.items = data.employees.items.filter((row) => row.change !== "UNCHANGED");
    const after = url.searchParams.get("after");
    if (after)
      data.employees.items = data.employees.items.filter((row) => row.employmentId > after);
    const hasNext = data.employees.items.length > 50;
    data.employees.items = data.employees.items.slice(0, 50);
    data.employees.nextCursor = hasNext
      ? (data.employees.items.at(-1)?.employmentId ?? null)
      : null;
    if (broken) data.summary.delta.takeHome = "0.00";
    return route.fulfill({ json: data });
  });
  return {
    ...api,
    comparisonReads: reads,
    breakResponse: () => {
      broken = true;
    },
    deny: () => {
      denied = true;
    },
    hold: () => {
      let entered!: () => void, release!: () => void;
      const started = new Promise<void>((done) => {
          entered = done;
        }),
        wait = new Promise<void>((done) => {
          release = done;
        });
      held = { entered, wait };
      return { started, release };
    },
  };
}
test("compare original periods, review exact employee amounts and filter changes in Indonesian dark mobile layout", async ({
  page,
}) => {
  const api = await install(page);
  await page.goto(`/payroll/runs/${calculationId}`);
  await page.getByRole("link", { name: "Period comparison", exact: true }).click();
  await expect(page.getByLabel("Earlier earnings month", { exact: true })).toHaveValue("2026-08");
  expect(api.comparisonReads).toHaveLength(0);
  await page.getByRole("button", { name: "Compare: 2026-08 · 2026-09-02", exact: true }).click();
  const employees = page.getByRole("table", { name: "Employee comparison", exact: true });
  await expect(employees.getByRole("row")).toHaveCount(5);
  await expect(employees).toContainText("Departed employee");
  await expect(employees).toContainText("New employee");
  await expect(page.getByRole("table", { name: "Payroll totals", exact: true })).toContainText(
    "10.02",
  );
  await page.getByRole("button", { name: "Review employee: Updated name", exact: true }).click();
  const panel = page.getByRole("dialog", { name: "Employee amounts", exact: true });
  await expect(panel).toContainText("Original name");
  await expect(panel).toContainText("Updated name");
  await expect(panel.getByRole("link", { name: "Baseline result", exact: true })).toHaveAttribute(
    "href",
    new RegExp(comparisonBaseline),
  );
  await expect(panel.getByRole("table", { name: "Employee amounts", exact: true })).toContainText(
    "0.01",
  );
  await panel.getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("checkbox", { name: "Show changes only", exact: true }).click();
  await expect(
    page.getByRole("checkbox", { name: "Show changes only", exact: true }),
  ).toBeChecked();
  await expect(employees.getByRole("row")).toHaveCount(4);
  expect(api.comparisonReads).toHaveLength(2);
  expect(api.comparisonReads[1]?.searchParams.get("changedOnly")).toBe("true");
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(
    page.getByRole("heading", { name: "Perbandingan periode", exact: true }),
  ).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: "../../.work/dashboard-payroll-comparison-id.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Tinjau karyawan: Updated name", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "Nominal karyawan", exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  expect(api.writes).toHaveLength(0);
  expect(api.identity.unhandled).toEqual([]);
});
test("company replacement rejects a late payroll comparison", async ({ page }) => {
  const api = await install(page),
    held = api.hold();
  await page.goto(`${path}?company=${companyIds[0]}&baselineId=${comparisonBaseline}`);
  await held.started;
  await page
    .getByRole("combobox", { name: "Company", exact: true })
    .selectOption(companyIds[1] as string);
  held.release();
  await expect(page).toHaveURL(new RegExp(`/payroll/periods\\?company=${companyIds[1]}`));
  await expect(page.getByRole("table", { name: "Employee comparison", exact: true })).toHaveCount(
    0,
  );
  expect(api.comparisonReads).toHaveLength(1);
  expect(api.identity.unhandled).toEqual([]);
});
test("hidden observations and employee panels clear before a single foreground reload and revoked access", async ({
  page,
}) => {
  const api = await install(page);
  await page.goto(`${path}?baselineId=${comparisonBaseline}`);
  await page.getByRole("button", { name: "Review employee: Updated name", exact: true }).click();
  await page.evaluate(() => {
    Object.defineProperty(document, "visibilityState", { value: "hidden", configurable: true });
    document.dispatchEvent(new Event("visibilitychange"));
  });
  await expect(page.getByRole("dialog", { name: "Employee amounts", exact: true })).toHaveCount(0);
  expect(api.comparisonReads).toHaveLength(1);
  await page.evaluate(() => {
    Object.defineProperty(document, "visibilityState", { value: "visible", configurable: true });
    document.dispatchEvent(new Event("visibilitychange"));
  });
  await expect(page.getByRole("table", { name: "Employee comparison", exact: true })).toBeVisible();
  expect(api.comparisonReads).toHaveLength(2);
  api.identity.setPermissions([]);
  api.deny();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("table", { name: "Employee comparison", exact: true })).toHaveCount(
    0,
  );
  expect(api.identity.unhandled).toEqual([]);
});
test("invalid monetary arithmetic clears old totals without retrying the comparison", async ({
  page,
}) => {
  const api = await install(page);
  await page.goto(`${path}?baselineId=${comparisonBaseline}`);
  await expect(page.getByRole("table", { name: "Payroll totals", exact: true })).toBeVisible();
  api.breakResponse();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("table", { name: "Payroll totals", exact: true })).toHaveCount(0);
  expect(api.comparisonReads).toHaveLength(2);
  expect(api.writes).toHaveLength(0);
});

test("employee cursor pages keep whole-run totals and return to the first bounded page", async ({
  page,
}) => {
  const api = await install(page, true);
  await page.goto(`${path}?company=${companyIds[0]}&baselineId=${comparisonBaseline}`);
  const table = page.getByRole("table", { name: "Employee comparison", exact: true });
  await expect(table.getByRole("row")).toHaveCount(51);
  await expect(table).not.toContainText("Employee 051");
  const totals = page.getByRole("table", { name: "Payroll totals", exact: true });
  const retained = await totals.getByRole("cell").allTextContents();
  await page.getByRole("button", { name: "Next employee page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(2);
  await expect(table).toContainText("Employee 051");
  await expect(totals.getByRole("cell")).toHaveText(retained);
  expect(api.comparisonReads[1]?.searchParams.get("after")).toBe(
    "cf000000-0000-4000-8000-000000000050",
  );
  await expect(
    page.getByRole("button", { name: "Next employee page", exact: true }),
  ).toBeDisabled();
  await page.getByRole("button", { name: "First employee page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(51);
  expect(api.comparisonReads[2]?.searchParams.get("after")).toBeNull();
  expect(api.comparisonReads).toHaveLength(3);
  expect(api.writes).toEqual([]);
});
