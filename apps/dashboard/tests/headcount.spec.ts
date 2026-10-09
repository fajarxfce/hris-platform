import { expect, type Page, test } from "@playwright/test";
import { companyIds, installIdentityApi } from "./identity-api";

const permissions = ["company.read", "people.read", "reports.read"];
const path = "/reports/headcount?asOf=2026-10-01";

function report(companies: string[], asOf: string, employments = 12) {
  const counts = {
    employments,
    persons: employments - 1,
    active: employments - 2,
    probation: 1,
    suspended: 1,
    permanent: employments - 3,
    fixedTerm: 3,
  };
  const totals = Object.fromEntries(
    Object.entries(counts).map(([key, value]) => [key, value * companies.length]),
  );
  totals.persons = counts.persons * companies.length - (companies.length - 1);
  return {
    asOf,
    evaluatedAt: "2026-10-01T00:00:00Z",
    definitionVersion: "headcount.v1",
    totals,
    companies: companies.map((companyId) => ({ companyId, counts })),
  };
}

async function installReports(page: Page) {
  const requests: string[] = [];
  let failure = false;
  await page.route("**/api/v1/reports/headcount?*", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    requests.push(url.pathname + url.search);
    expect(request.headers()["x-hris-client-platform"]).toBe("WEB");
    expect(request.headers()["x-hris-client-build"]).toBe("1");
    if (failure)
      return route.fulfill({
        status: 503,
        json: {
          code: "company_maintenance",
          fields: {},
          parameters: {},
          retryAfterSeconds: 604_800,
          detail: "INTERNAL OPERATIONAL TEXT",
        },
      });
    const asOf = url.searchParams.get("asOf") ?? "";
    return route.fulfill({
      json: report(
        (url.searchParams.get("companies") ?? "").split(","),
        asOf,
        asOf === "2026-10-02" ? 13 : 12,
      ),
    });
  });
  return {
    requests,
    fail: () => {
      failure = true;
    },
  };
}

test("reports use URL dates, localized counts and responsive Fluent layouts", async ({ page }) => {
  const identity = await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installReports(page);
  await page.goto(path);
  await expect(page.getByRole("heading", { name: "Headcount" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Reports", exact: true })).toHaveAttribute(
    "aria-current",
    "page",
  );
  await expect(page.getByRole("region", { name: "Employment records", exact: true })).toContainText(
    "12",
  );
  await expect(page.getByRole("region", { name: "Unique people", exact: true })).toContainText(
    "11",
  );
  await page.screenshot({
    path: "../../.work/dashboard-headcount-light.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByLabel("As of date", { exact: true }).fill("2026-10-02");
  await expect(page).toHaveURL(/asOf=2026-10-01/u);
  await expect(page.getByLabel("As of date", { exact: true })).toHaveValue("2026-10-02");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page).toHaveURL(/asOf=2026-10-02/u);
  await expect(page.getByRole("region", { name: "Employment records", exact: true })).toContainText(
    "13",
  );
  await page.goBack();
  await expect(page.getByLabel("As of date", { exact: true })).toHaveValue("2026-10-01");
  await expect(page.getByRole("region", { name: "Employment records", exact: true })).toContainText(
    "12",
  );
  const loaded = api.requests.length;
  await page.getByRole("combobox", { name: "Language" }).selectOption("id");
  await expect(page.getByRole("region", { name: "Hubungan kerja", exact: true })).toContainText(
    "12",
  );
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByRole("button", { name: "Tema", exact: true })).toHaveText("Terang");
  expect(
    await page
      .getByLabel("Tanggal laporan", { exact: true })
      .evaluate((element) => getComputedStyle(element).colorScheme),
  ).toBe("dark");
  await page.screenshot({
    path: "../../.work/dashboard-headcount-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({
    path: "../../.work/dashboard-headcount-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  expect(api.requests).toHaveLength(loaded);
  expect(identity.unhandled).toEqual([]);
});

test("company switches discard delayed responses and logout clears reports", async ({ page }) => {
  const identity = await installIdentityApi(page, { signedIn: true, permissions });
  let enter!: () => void;
  let release!: () => void;
  const entered = new Promise<void>((resolve) => {
    enter = resolve;
  });
  const held = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route("**/api/v1/reports/headcount?*", async (route) => {
    const company = new URL(route.request().url()).searchParams.get("companies") ?? "";
    if (company === companyIds[0]) {
      enter();
      await held;
    }
    await route.fulfill({
      json: report([company], "2026-10-01", company === companyIds[0] ? 29 : 7),
    });
  });
  try {
    await page.goto(path);
    await entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(
      page.getByRole("region", { name: "Employment records", exact: true }),
    ).toContainText("7");
    release();
    await expect(page.getByRole("main")).not.toContainText("North Company");
    await expect(
      page.getByRole("region", { name: "Employment records", exact: true }),
    ).not.toContainText("29");
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

test("company filters apply one group snapshot, preserve distinct totals and restore with browser history", async ({
  page,
}) => {
  const identity = await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installReports(page);
  await page.goto(path);
  await expect(page.getByRole("region", { name: "Employment records", exact: true })).toContainText(
    "12",
  );
  const requests = api.requests.length;
  const selection = page.getByRole("combobox", { name: "Companies", exact: true });
  await selection.click();
  await page.getByRole("menuitemcheckbox", { name: "South Company (SOUTH)", exact: true }).click();
  await page.keyboard.press("Escape");
  await expect(selection).toContainText("2 companies selected");
  expect(api.requests).toHaveLength(requests);
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page.getByRole("region", { name: "Employment records", exact: true })).toContainText(
    "24",
  );
  await expect(page.getByRole("region", { name: "Unique people", exact: true })).toContainText(
    "21",
  );
  const table = page.getByRole("table", { name: "By company" });
  await expect(table.getByRole("row")).toHaveCount(3);
  await expect(table.getByRole("row").filter({ hasText: "North Company" })).toContainText("11");
  await expect(table.getByRole("row").filter({ hasText: "South Company" })).toContainText("11");
  await page.screenshot({
    path: "../../.work/dashboard-group-headcount-light.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.goBack();
  await expect(selection).toContainText("North Company");
  await expect(page.getByRole("region", { name: "Employment records", exact: true })).toContainText(
    "12",
  );
  await expect(table).toHaveCount(0);
  await page.goForward();
  await expect(selection).toContainText("2 companies selected");
  await expect(table).toBeVisible();
  const loaded = api.requests.length;
  await page.getByRole("combobox", { name: "Language" }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole("table", { name: "Per perusahaan" })).toBeVisible();
  await page.screenshot({
    path: "../../.work/dashboard-group-headcount-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  expect(api.requests).toHaveLength(loaded);
  expect(identity.unhandled).toEqual([]);
});

test("invalid company selections fail locally and a rejected group never shows partial counts", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installReports(page);
  await page.goto(`${path}&companies=${companyIds[0]},${companyIds[0]}`);
  await expect(page.getByRole("alert")).toContainText(
    "Select between 1 and 32 different companies.",
  );
  expect(api.requests).toEqual([]);
  await page.goto(`${path}&companies=${companyIds.join(",")}`);
  await expect(page.getByRole("region", { name: "Employment records", exact: true })).toContainText(
    "24",
  );
  api.fail();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Company services are under maintenance.");
  await expect(page.getByRole("table")).toHaveCount(0);
  await expect(page.getByRole("region", { name: "Unique people", exact: true })).toHaveCount(0);
});

test("changing a pending group selection discards its late result", async ({ page }) => {
  await installIdentityApi(page, { signedIn: true, permissions });
  let enter!: () => void;
  let release!: () => void;
  const entered = new Promise<void>((resolve) => {
    enter = resolve;
  });
  const held = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route("**/api/v1/reports/headcount?*", async (route) => {
    const companies = (new URL(route.request().url()).searchParams.get("companies") ?? "").split(
      ",",
    );
    if (companies.length > 1) {
      enter();
      await held;
    }
    await route.fulfill({ json: report(companies, "2026-10-01", companies.length > 1 ? 29 : 7) });
  });
  try {
    await page.goto(`${path}&companies=${companyIds.join(",")}`);
    await entered;
    await page.getByRole("combobox", { name: "Companies", exact: true }).click();
    await page
      .getByRole("menuitemcheckbox", { name: "South Company (SOUTH)", exact: true })
      .click();
    await page.keyboard.press("Escape");
    await page.getByRole("button", { name: "Apply", exact: true }).click();
    await expect(
      page.getByRole("region", { name: "Employment records", exact: true }),
    ).toContainText("7");
    release();
    await expect(
      page.getByRole("region", { name: "Employment records", exact: true }),
    ).not.toContainText("58");
    await expect(page.getByRole("table")).toHaveCount(0);
  } finally {
    release();
  }
});

test("direct links without report permission cannot fetch company totals", async ({ page }) => {
  const identity = await installIdentityApi(page, { signedIn: true });
  const api = await installReports(page);
  await page.goto(path);
  await expect(page.getByRole("alert")).toContainText("You do not have access to this resource.");
  await expect(page.getByRole("link", { name: "Reports", exact: true })).toHaveCount(0);
  await expect(page.getByRole("region", { name: "Employment records", exact: true })).toHaveCount(
    0,
  );
  expect(api.requests).toEqual([]);
  expect(identity.unhandled).toEqual([]);
});

test("invalid date links and server availability failures leave no obsolete counts", async ({
  page,
}) => {
  const identity = await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installReports(page);
  await page.goto("/reports/headcount?asOf=2026-02-29");
  await expect(page.getByRole("alert")).toContainText("Choose a valid date between 1900 and 2100.");
  expect(api.requests).toEqual([]);
  await page.getByLabel("As of date", { exact: true }).fill("2026-10-01");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page.getByRole("region", { name: "Employment records", exact: true })).toContainText(
    "12",
  );
  api.fail();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Company services are under maintenance.");
  await expect(page.getByRole("region", { name: "Employment records", exact: true })).toHaveCount(
    0,
  );
  await expect(page.locator("body")).not.toContainText("INTERNAL OPERATIONAL");
  await page.getByRole("combobox", { name: "Language" }).selectOption("id");
  await expect(page.getByRole("alert")).toContainText(
    "Layanan perusahaan sedang dalam maintenance.",
  );
  expect(identity.unhandled).toEqual([]);
});
