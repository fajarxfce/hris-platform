import { expect, type Page, test } from "@playwright/test";
import { companyIds, installIdentityApi } from "./identity-api";

const permissions = ["company.read", "workforce.read", "reports.read"];
const path = "/reports/overtime?from=2026-08-31&until=2026-10-01";

function report(
  companyId: string,
  from: string,
  until: string,
  employments = 1,
  status: string | null = null,
) {
  const first = new Date(`${from.slice(0, 7)}-01T00:00:00Z`);
  const length =
    (Number(until.slice(0, 4)) - first.getUTCFullYear()) * 12 +
    Number(until.slice(5, 7)) -
    first.getUTCMonth();
  const counts = {
    requests: employments,
    employments,
    actualRequests: status === "PLANNED" ? 0 : employments,
    requestedMinutes: 180 * employments,
    actualMinutes: status === "PLANNED" ? 0 : 135 * employments,
    approvedMinutes: status === "APPROVED" || status === null ? 135 * employments : 0,
  };
  return {
    companyId,
    from,
    until,
    status,
    evaluatedAt: "2026-10-01T00:00:00Z",
    definitionVersion: "overtime.v1",
    totals: {
      requests: length * counts.requests,
      employments,
      actualRequests: length * counts.actualRequests,
      requestedMinutes: length * counts.requestedMinutes,
      actualMinutes: length * counts.actualMinutes,
      approvedMinutes: length * counts.approvedMinutes,
    },
    months: Array.from({ length }, (_, index) => {
      const date = new Date(Date.UTC(first.getUTCFullYear(), first.getUTCMonth() + index, 1));
      const month = date.toISOString().slice(0, 7);
      return {
        month,
        from: index === 0 ? from : `${month}-01`,
        until:
          index === length - 1
            ? until
            : new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + 1, 0))
                .toISOString()
                .slice(0, 10),
        counts,
      };
    }),
  };
}

async function installReports(page: Page) {
  const requests: string[] = [];
  let failing = false;
  await page.route("**/api/v1/companies/*/reports/overtime?*", async (route) => {
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
        1,
        url.searchParams.get("status"),
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

test("overtime reports apply annual dates and status, retain browser history and open clipped month requests on narrow screens", async ({
  page,
}) => {
  const identity = await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installReports(page);
  const drilldowns: string[] = [];
  await page.route("**/workforce/overtime?*", (route) => {
    const url = new URL(route.request().url());
    if (!url.pathname.startsWith("/api/")) return route.fallback();
    drilldowns.push(url.pathname + url.search);
    return route.fulfill({ json: { items: [], nextCursor: null } });
  });
  await page.goto(path);
  await expect(page.getByRole("heading", { name: "Overtime report", exact: true })).toBeVisible();
  await expect(page.getByRole("region", { name: "Requests", exact: true })).toContainText("3");
  await expect(page.getByRole("region", { name: "Employment records", exact: true })).toContainText(
    "1",
  );
  await expect(page.getByRole("region", { name: "Approved minutes", exact: true })).toContainText(
    "405",
  );
  const reads = api.requests.length;
  await page.getByLabel("From date", { exact: true }).fill("2026-09-01");
  await page.getByLabel("Status", { exact: true }).selectOption("PENDING");
  expect(api.requests).toHaveLength(reads);
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page.getByRole("region", { name: "Requests", exact: true })).toContainText("2");
  await expect(page.getByRole("region", { name: "Approved minutes", exact: true })).toContainText(
    "0",
  );
  expect(api.requests.at(-1)).toContain("status=PENDING");
  await page.goBack();
  await expect(page.getByLabel("From date", { exact: true })).toHaveValue("2026-08-31");
  await expect(page.getByLabel("Status", { exact: true })).toHaveValue("");
  await expect(page.getByRole("region", { name: "Requests", exact: true })).toContainText("3");
  await page.goForward();
  await expect(page.getByLabel("Status", { exact: true })).toHaveValue("PENDING");
  await expect(page.getByRole("region", { name: "Requests", exact: true })).toContainText("2");
  await page.getByRole("combobox", { name: "Language" }).selectOption("id");
  await expect(page.getByRole("heading", { name: "Laporan lembur", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  const action = page.getByRole("button", { name: /^Lihat pengajuan:/u }).last();
  await expect
    .poll(() =>
      action.evaluate((element) => {
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
    path: "../../.work/dashboard-overtime-report-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await action.click();
  await expect(page).toHaveURL(
    /\/workforce\/overtime\?company=.*&from=2026-10-01&until=2026-10-01&status=PENDING/u,
  );
  await expect.poll(() => drilldowns.length).toBe(1);
  const opened = new URL(drilldowns[0] ?? "", "http://fixture.invalid");
  expect(opened.pathname).toBe(`/api/v1/companies/${companyIds[0]}/workforce/overtime`);
  expect(Object.fromEntries(opened.searchParams)).toEqual({
    from: "2026-10-01",
    until: "2026-10-01",
    limit: "50",
    status: "PENDING",
  });
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
  await page.route("**/api/v1/companies/*/reports/overtime?*", async (route) => {
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

test("invalid URL ranges and missing report permission issue no report requests", async ({
  page,
}) => {
  const identity = await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installReports(page);
  await page.goto("/reports/overtime?from=2024-01-01&until=2025-01-01");
  await expect(page.getByRole("main")).toContainText(
    "Choose a date range of up to 366 days between 2000 and 2100",
  );
  expect(api.requests).toHaveLength(0);
  await page.goto(`${path}&status=INVALID`);
  await expect(page.getByRole("main")).toContainText("Choose a valid report status.");
  await expect(page.getByRole("region", { name: "Employment records", exact: true })).toHaveCount(
    0,
  );
  expect(api.requests).toHaveLength(0);
  identity.setPermissions(["company.read", "workforce.read"]);
  await page.goto(path);
  await expect(page.getByRole("link", { name: "Overtime report", exact: true })).toHaveCount(0);
  await expect(page.getByRole("region", { name: "Employment records", exact: true })).toHaveCount(
    0,
  );
  expect(api.requests).toHaveLength(0);
  expect(identity.unhandled).toEqual([]);
});
