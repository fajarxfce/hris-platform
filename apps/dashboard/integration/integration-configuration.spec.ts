import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { integrationConfigurationDto } from "../src/features/administration/data/models/integration-configuration-dto";
import { createAdminCompany } from "./fixtures/admin-company";
import { signInAndEnrollAuthenticator } from "./fixtures/sign-in-and-enroll-authenticator";

test("real API configuration is platform-only and loses visibility after independent access revocation", async ({
  page,
  context,
  browser,
  baseURL,
}) => {
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-integration-admin@example.invalid",
    "INTEGRATION_CONFIG",
    "Integration Configuration Acceptance",
  );
  await page.getByRole("combobox", { name: "Bahasa", exact: true }).selectOption("en");
  const admin = "a0000000-0000-4000-8000-000000000092";
  const member = "a0000000-0000-4000-8000-000000000093";
  const path = "/api/v1/administration/integrations/configuration";
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const assigned = await context.request.put(`/api/v1/companies/${company}/members/${member}`, {
    headers: headers(),
    data: {
      active: true,
      permissions: ["company.read", "settings.manage", "identity.manage"],
      reason: "Independent company-only integration review",
    },
  });
  expect(assigned.status()).toBe(200);
  await page.goto("/platform/integrations");
  const table = page.getByRole("table", { name: "Configured services", exact: true });
  await expect(table.getByRole("row")).toHaveCount(7);
  await expect(table.getByRole("row").filter({ hasText: "Single sign-on (OIDC)" })).toContainText(
    "Enabled",
  );
  await expect(
    table.getByRole("row").filter({ hasText: "Push notifications (FCM)" }),
  ).toContainText("Disabled");
  const response = await context.request.get(path);
  expect(response.status()).toBe(200);
  const configuration = integrationConfigurationDto.parse(await response.json());
  expect(configuration.integrations.find((entry) => entry.kind === "OIDC")?.enabled).toBe(true);
  expect(configuration.integrations.find((entry) => entry.kind === "PUSH")?.enabled).toBe(false);
  await page.screenshot({ path: "../../.work/integration-configuration-real.png", fullPage: true });
  if (!baseURL) throw new Error("Browser fixture URL required");
  const memberContext = await browser.newContext({ baseURL });
  const operatorContext = await browser.newContext({ baseURL });
  try {
    const memberPage = await memberContext.newPage();
    await signInAndEnrollAuthenticator(
      memberPage,
      "browser-integration-company-admin@example.invalid",
    );
    await memberPage.goto("/platform/integrations");
    await expect(memberPage.getByRole("alert")).toContainText("platform administration");
    await expect(
      memberPage.getByRole("table", { name: "Configured services", exact: true }),
    ).toHaveCount(0);
    expect((await memberContext.request.get(path)).status()).toBe(403);
    const operator = await operatorContext.newPage();
    await signInAndEnrollAuthenticator(operator, "browser-integration-operator@example.invalid");
    const token = await (await operatorContext.request.get("/api/v1/auth/csrf")).json();
    const account = await operatorContext.request.get(`/api/v1/identity/accounts/${admin}`);
    expect(account.status()).toBe(200);
    const version = (await account.json()).account.version;
    const revoked = await operatorContext.request.put(`/api/v1/identity/accounts/${admin}/access`, {
      headers: { [token.headerName]: token.token, "Idempotency-Key": randomUUID() },
      data: {
        expectedVersion: version,
        active: true,
        platformPermissions: ["companies.create"],
        reason: "Revoke independent integration reviewer platform access",
      },
    });
    expect(revoked.status()).toBe(200);
    expect((await context.request.get(path)).status()).toBe(401);
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(table).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Sign in", exact: true })).toBeVisible();
    expect((await memberContext.request.get(path)).status()).toBe(403);
  } finally {
    await memberContext.close();
    await operatorContext.close();
  }
});
