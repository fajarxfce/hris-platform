import { expect, type Page, test } from "@playwright/test";
import { installIdentityApi } from "./identity-api";

const path = "/platform/integrations";
async function fixture(page: Page, administrator = true) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfaConfigured: true,
    noCompanies: administrator,
    platformPermissions: administrator ? ["identity.manage"] : [],
    permissions: ["company.read", "identity.manage", "settings.manage"],
  });
  let failure: string | null = null;
  const reads: string[] = [];
  await page.route("**/api/v1/administration/integrations/configuration", (route) => {
    reads.push(route.request().method());
    return failure
      ? route.fulfill({
          status: failure === "connection_unavailable" ? 503 : 403,
          json: { code: failure, detail: "PRIVATE PROVIDER CONFIGURATION" },
        })
      : route.fulfill({
          json: {
            runtime: "API",
            observedAt: "2026-10-28T16:00:00Z",
            integrations: [
              "FIREBASE_AUTH",
              "OIDC",
              "MAIL",
              "PUSH",
              "STORAGE",
              "DOCUMENT_SCANNER",
            ].map((kind) => ({ kind, enabled: kind === "FIREBASE_AUTH" || kind === "STORAGE" })),
          },
        });
  });
  return {
    identity,
    reads,
    fail: (code: string | null) => {
      failure = code;
    },
  };
}

test("a platform administrator without companies reviews six flags and localized narrow layout", async ({
  page,
}) => {
  const f = await fixture(page);
  await page.goto(path);
  const table = page.getByRole("table", { name: "Configured services", exact: true });
  await expect(table.getByRole("row")).toHaveCount(7);
  await expect(table.getByRole("row").filter({ hasText: "Firebase Authentication" })).toContainText(
    "Enabled",
  );
  await expect(
    table.getByRole("row").filter({ hasText: "Push notifications (FCM)" }),
  ).toContainText("Disabled");
  await expect(page.getByText("API startup settings.", { exact: false })).toBeVisible();
  await expect(
    page
      .getByRole("navigation")
      .getByRole("link", { name: "Integration configuration", exact: true }),
  ).toHaveAttribute("aria-current", "page");
  await page.screenshot({
    path: "../../.work/integration-configuration-desktop.png",
    fullPage: true,
  });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(
    page.getByRole("table", { name: "Layanan terkonfigurasi", exact: true }),
  ).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  expect(f.reads).toEqual(["GET"]);
  expect(f.identity.unhandled).toEqual([]);
  await page.screenshot({
    path: "../../.work/integration-configuration-mobile.png",
    fullPage: true,
  });
});

test("company administration does not grant platform integration visibility or issue its read", async ({
  page,
}) => {
  const f = await fixture(page, false);
  await page.goto(path);
  await expect(page.getByRole("alert")).toContainText("platform administration");
  await expect(
    page
      .getByRole("navigation")
      .getByRole("link", { name: "Integration configuration", exact: true }),
  ).toHaveCount(0);
  await expect(page.getByRole("table", { name: "Configured services", exact: true })).toHaveCount(
    0,
  );
  expect(f.reads).toEqual([]);
  expect(f.identity.unhandled).toEqual([]);
});

test("failed refresh removes old flags and fresh MFA restores an admitted observation", async ({
  page,
}) => {
  const f = await fixture(page);
  await page.goto(path);
  const table = page.getByRole("table", { name: "Configured services", exact: true });
  await expect(table).toBeVisible();
  f.fail("connection_unavailable");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(table).toHaveCount(0);
  await expect(page.getByRole("alert")).toContainText("Unable to connect");
  f.identity.expireMfa();
  f.fail("mfa_required");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const dialog = page.getByRole("dialog", { name: "Account verification", exact: true });
  await dialog.getByLabel("Authenticator code", { exact: true }).fill("123456");
  f.fail(null);
  await dialog.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(table).toBeVisible();
  await expect(page.getByText("PRIVATE PROVIDER CONFIGURATION", { exact: false })).toHaveCount(0);
  expect(f.reads.every((method) => method === "GET")).toBe(true);
  expect(f.identity.unhandled).toEqual([]);
});

test("revoked platform access discards the observation and removes its navigation entry", async ({
  page,
}) => {
  const f = await fixture(page);
  await page.goto(path);
  const table = page.getByRole("table", { name: "Configured services", exact: true });
  await expect(table).toBeVisible();
  f.identity.setPlatformPermissions([]);
  f.fail("platform_administrator_required");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(table).toHaveCount(0);
  await expect(
    page
      .getByRole("navigation")
      .getByRole("link", { name: "Integration configuration", exact: true }),
  ).toHaveCount(0);
  await expect(page.getByRole("alert")).toContainText("platform administration");
  expect(f.reads).toEqual(["GET", "GET"]);
  expect(f.identity.unhandled).toEqual([]);
});
