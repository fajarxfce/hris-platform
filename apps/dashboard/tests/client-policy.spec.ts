import { expect, type Page, test } from "@playwright/test";
import { companyIds, installIdentityApi } from "./identity-api";

const permissions = ["company.read", "settings.manage"];
const path = "/settings/client-policy";
const revision = (version: number, company = companyIds[0] as string) => ({
  version,
  activateAt: version === 0 ? "2026-10-01T00:00:00Z" : "2026-10-11T00:00:00Z",
  disabledModules: version === 0 ? ["EXPENSES"] : ["PAYROLL"],
  minimumBuilds: { android: version + 2, ios: version + 1, web: 0 },
  maintenance:
    version === 0
      ? null
      : {
          startsAt: "2026-10-11T00:00:00Z",
          endsAt: "2026-10-11T00:15:00Z",
        },
  recordedAt: "2026-09-30T00:00:00Z",
  actorId: "20000000-0000-4000-8000-000000000001",
  reason: `${company === companyIds[0] ? "North" : "South"} revision ${version}`,
});
const settings = (company = companyIds[0] as string) => ({
  latest: revision(1, company),
  effective: {
    schemaVersion: 1,
    version: 0,
    enabledModules: [
      "PEOPLE",
      "WORKFORCE",
      "LEAVE",
      "PAYROLL",
      "DOCUMENTS",
      "COMMUNICATIONS",
      "REPORTING",
    ],
    minimumBuilds: { android: 2, ios: 1, web: 0 },
    maintenance: null,
    maintenanceActive: false,
    serverTime: "2026-10-10T00:00:00.000000123Z",
    validUntil: "2026-10-10T00:01:00.000000123Z",
  },
});

async function installPolicies(page: Page) {
  const requests: URL[] = [];
  let failure: string | null = null;
  await page.route("**/api/v1/companies/*/settings/client-policy{,/**}", async (route) => {
    const url = new URL(route.request().url());
    requests.push(url);
    expect(route.request().method()).toBe("GET");
    expect(route.request().headers()["x-hris-client-platform"]).toBe("WEB");
    const company = url.pathname.split("/")[4] ?? "";
    const version = url.pathname.split("/revisions/")[1];
    const code =
      failure ?? (version && Number(version) > 1 ? "client_policy_revision_not_found" : null);
    return code
      ? route.fulfill({
          status: 403,
          json: {
            code,
            fields: {},
            parameters: {},
            detail: "PRIVATE SETTINGS FAILURE",
          },
        })
      : route.fulfill({
          json: version === undefined ? settings(company) : revision(Number(version), company),
        });
  });
  return {
    requests,
    fail: (code: string | null) => {
      failure = code;
    },
  };
}

test("client policy separates configured and effective revisions in localized responsive layouts", async ({
  page,
}) => {
  const identity = await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installPolicies(page);
  await page.goto(path);
  await expect(page.getByRole("link", { name: "Client policy", exact: true })).toHaveAttribute(
    "aria-current",
    "page",
  );
  const effective = page.getByRole("region", { name: "Effective policy", exact: true });
  await expect(
    effective
      .locator(".app-property-row")
      .filter({ has: page.getByText("Revision", { exact: true }) }),
  ).toContainText("0");
  await expect(
    effective.locator(".app-property-row").filter({ hasText: "Latest configured revision" }),
  ).toContainText("1");
  const configuration = page.getByRole("region", { name: "Configuration revision", exact: true });
  await expect(configuration).toContainText("North revision 1");
  const modules = page.getByRole("table", { name: "Modules", exact: true });
  await expect(modules.getByRole("row")).toHaveCount(9);
  await expect(modules.getByRole("row").filter({ hasText: "Payroll" })).toContainText("Enabled");
  await expect(modules.getByRole("row").filter({ hasText: "Expenses" })).toContainText("Disabled");
  await page.screenshot({
    path: "../../.work/dashboard-client-policy-light.png",
    fullPage: true,
    animations: "disabled",
  });
  const loaded = api.requests.length;
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Policy client", exact: true })).toBeVisible();
  await page.screenshot({
    path: "../../.work/dashboard-client-policy-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({
    path: "../../.work/dashboard-client-policy-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  expect(api.requests).toHaveLength(loaded);
  expect(identity.unhandled).toEqual([]);
});

test("revision history preserves browser navigation and does not fetch on an unfinished edit", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installPolicies(page);
  await page.goto(path);
  const configuration = page.getByRole("region", { name: "Configuration revision", exact: true });
  await expect(configuration).toContainText("North revision 1");
  const input = page.getByLabel("Revision", { exact: true });
  const loaded = api.requests.length;
  await input.fill("0");
  expect(api.requests).toHaveLength(loaded);
  await page.getByRole("button", { name: "View revision", exact: true }).click();
  await expect(configuration).toContainText("North revision 0");
  await expect(page).toHaveURL(new RegExp(`company=${companyIds[0]}&version=0$`, "u"));
  expect(api.requests.at(-1)?.pathname).toBe(
    `/api/v1/companies/${companyIds[0]}/settings/client-policy/revisions/0`,
  );
  await page.goBack();
  await expect(configuration).toContainText("North revision 1");
  await expect(input).toHaveValue("");
  await page.goForward();
  await expect(configuration).toContainText("North revision 0");
  await expect(input).toHaveValue("0");
  await input.fill("5");
  await page.getByRole("button", { name: "Latest revision", exact: true }).click();
  await expect(configuration).toContainText("North revision 1");
  await expect(input).toHaveValue("");
});

test("invalid and missing revisions recover explicitly without automatic retries or raw server messages", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installPolicies(page);
  await page.goto(`${path}?version=10000`);
  await expect(page.getByRole("alert")).toContainText("Enter a revision from 0 to 9999.");
  expect(api.requests).toEqual([]);
  await page.getByRole("button", { name: "Latest revision", exact: true }).click();
  await expect(page.getByRole("region", { name: "Effective policy", exact: true })).toBeVisible();
  await page.getByLabel("Revision", { exact: true }).fill("9999");
  await page.getByRole("button", { name: "View revision", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("The configuration revision was not found.");
  await expect(page.getByRole("table")).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText("PRIVATE SETTINGS FAILURE");
  const failed = api.requests.length;
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByRole("alert")).toContainText("Revisi konfigurasi tidak ditemukan.");
  expect(api.requests).toHaveLength(failed);
  await page.getByRole("button", { name: "Revisi terbaru", exact: true }).click();
  await expect(page.getByRole("region", { name: "Policy efektif", exact: true })).toBeVisible();
});

test("company changes discard an in-flight history response and its foreign revision selection", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installPolicies(page);
  let enter!: () => void;
  let release!: () => void;
  const entered = new Promise<void>((resolve) => {
    enter = resolve;
  });
  const held = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route(
    `**/api/v1/companies/${companyIds[0]}/settings/client-policy/revisions/0`,
    async (route) => {
      enter();
      await held;
      await route.fulfill({ json: revision(0) });
    },
  );
  try {
    await page.goto(`${path}?company=${companyIds[0]}&version=0`);
    await entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    const configuration = page.getByRole("region", { name: "Configuration revision", exact: true });
    await expect(configuration).toContainText("South revision 1");
    await expect(page.getByLabel("Revision", { exact: true })).toHaveValue("");
    release();
    await expect(configuration).not.toContainText("North revision");
    expect(api.requests.at(-1)?.pathname).toBe(
      `/api/v1/companies/${companyIds[1]}/settings/client-policy`,
    );
    await page.getByRole("button", { name: "Sign out", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Sign in", exact: true })).toBeVisible();
    await expect(configuration).toHaveCount(0);
  } finally {
    release();
  }
});

test("direct links require settings access and refresh clears configuration after revocation", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true, permissions: ["company.read"] });
  const denied = await installPolicies(page);
  await page.goto(path);
  await expect(page.getByRole("link", { name: "Client policy", exact: true })).toHaveCount(0);
  await expect(page.getByRole("alert")).toContainText("You do not have access to this resource.");
  expect(denied.requests).toEqual([]);
  await installIdentityApi(page, { signedIn: true, permissions });
  const api = await installPolicies(page);
  await page.reload();
  await expect(page.getByRole("region", { name: "Effective policy", exact: true })).toBeVisible();
  const loaded = api.requests.length;
  api.fail("company_access_denied");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Company access is unavailable.");
  await expect(
    page.getByRole("region", { name: "Configuration revision", exact: true }),
  ).toHaveCount(0);
  expect(api.requests).toHaveLength(loaded + 1);
});
