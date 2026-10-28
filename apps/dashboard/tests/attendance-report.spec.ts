import { expect, type Page, test } from "@playwright/test";
import { companyIds, installIdentityApi } from "./identity-api";

const permissions = ["company.read", "workforce.read", "reports.read"];
const path = "/reports/attendance-exceptions?from=2026-09-29&until=2026-09-30";

function report(companyId: string, from: string, until: string, employments = 1) {
  const start = Date.parse(`${from}T00:00:00Z`);
  const length = (Date.parse(`${until}T00:00:00Z`) - start) / 86_400_000 + 1;
  const counts = {
    observedDays: employments,
    affectedDays: employments,
    affectedEmployments: employments,
    pendingDays: employments,
    incompleteDays: employments,
    pendingEvents: employments,
  };
  const totals = {
    observedDays: length * employments,
    affectedDays: length * employments,
    affectedEmployments: employments,
    pendingDays: length * employments,
    incompleteDays: length * employments,
    pendingEvents: length * employments,
  };
  return {
    companyId,
    from,
    until,
    evaluatedAt: "2026-10-01T00:00:00Z",
    definitionVersion: "attendance-exceptions.v1",
    totals,
    days: Array.from({ length }, (_, index) => ({
      date: new Date(start + index * 86_400_000).toISOString().slice(0, 10),
      counts,
    })),
  };
}

async function installReports(page: Page) {
  const requests: string[] = [];
  let failing = false;
  await page.route("**/api/v1/companies/*/reports/attendance-exceptions?*", async (route) => {
    const url = new URL(route.request().url());
    requests.push(url.pathname + url.search);
    expect(route.request().headers()["x-hris-client-platform"]).toBe("WEB");
    expect(route.request().headers()["x-hris-client-build"]).toBe("1");
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

test("attendance reports apply dates, localize, fit narrow screens and drill down to the same company/day", async ({
  page,
}) => {
  const identity = await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installReports(page);
  const drilldowns: string[] = [];
  await page.route("**/workforce/attendance/exceptions?*", (route) => {
    const url = new URL(route.request().url());
    if (!url.pathname.startsWith("/api/")) return route.fallback();
    drilldowns.push(url.pathname + url.search);
    return route.fulfill({ json: { items: [], nextCursor: null } });
  });
  await page.goto(path);
  await expect(page.getByRole("heading", { name: "Attendance report", exact: true })).toBeVisible();
  await expect(
    page.getByRole("region", { name: "Employee days with exceptions", exact: true }),
  ).toContainText("2");
  await expect(
    page.getByRole("region", { name: "Employment records affected", exact: true }),
  ).toContainText("1");
  const count = api.requests.length;
  await page.getByLabel("From date", { exact: true }).fill("2026-09-28");
  expect(api.requests).toHaveLength(count);
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(
    page.getByRole("region", { name: "Employee days with exceptions", exact: true }),
  ).toContainText("3");
  await page.goBack();
  await expect(page.getByLabel("From date", { exact: true })).toHaveValue("2026-09-29");
  await expect(
    page.getByRole("region", { name: "Employee days with exceptions", exact: true }),
  ).toContainText("2");
  await page.getByRole("combobox", { name: "Language" }).selectOption("id");
  await expect(page.getByRole("heading", { name: "Laporan presensi", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect
    .poll(() =>
      page
        .getByRole("button", { name: /^Lihat masalah:/u })
        .last()
        .evaluate((element) => {
          const bounds = element.getBoundingClientRect();
          const container = element.closest(".app-table-scroll")?.getBoundingClientRect();
          return (
            container !== undefined &&
            bounds.left >= container.left &&
            bounds.right <= container.right
          );
        }),
    )
    .toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-attendance-report-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page
    .getByRole("button", { name: /^Lihat masalah:/u })
    .last()
    .click();
  await expect(page).toHaveURL(
    /\/workforce\/attendance\/exceptions\?company=.*&from=2026-09-30&until=2026-09-30/u,
  );
  await expect.poll(() => drilldowns.length).toBe(1);
  expect(drilldowns[0]).toContain(
    `/companies/${companyIds[0]}/workforce/attendance/exceptions?from=2026-09-30&until=2026-09-30`,
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
  await page.route("**/api/v1/companies/*/reports/attendance-exceptions?*", async (route) => {
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
      page.getByRole("region", { name: "Employment records affected", exact: true }),
    ).toContainText("1");
    release();
    await expect(
      page.getByRole("region", { name: "Employment records affected", exact: true }),
    ).not.toContainText("9");
    await expect(page.getByRole("main")).not.toContainText("North Company");
    await page.getByRole("button", { name: "Sign out", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Sign in", exact: true })).toBeVisible();
    await expect(
      page.getByRole("region", { name: "Employment records affected", exact: true }),
    ).toHaveCount(0);
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
  await expect(
    page.getByRole("region", { name: "Employment records affected", exact: true }),
  ).toBeVisible();
  api.fail(true);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(
    page.getByRole("region", { name: "Employment records affected", exact: true }),
  ).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText("PRIVATE REPORT DETAIL");
  const failedRequests = api.requests.length;
  api.fail(false);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(
    page.getByRole("region", { name: "Employment records affected", exact: true }),
  ).toContainText("1");
  expect(api.requests).toHaveLength(failedRequests + 1);
  expect(identity.unhandled).toEqual([]);
});

test("invalid URL ranges and missing report permission issue no report requests", async ({
  page,
}) => {
  const identity = await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installReports(page);
  await page.goto("/reports/attendance-exceptions?from=2026-08-01&until=2026-09-30");
  await expect(page.getByRole("main")).toContainText("Choose a date range of up to 31 days");
  expect(api.requests).toHaveLength(0);
  identity.setPermissions(["company.read", "workforce.read"]);
  await page.goto(path);
  await expect(page.getByRole("link", { name: "Attendance report", exact: true })).toHaveCount(0);
  await expect(
    page.getByRole("region", { name: "Employment records affected", exact: true }),
  ).toHaveCount(0);
  expect(api.requests).toHaveLength(0);
  expect(identity.unhandled).toEqual([]);
});
