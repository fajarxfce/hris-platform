import { expect, test } from "@playwright/test";
import { signInAndEnrollAuthenticator } from "./fixtures/sign-in-and-enroll-authenticator";

test("real API platform administration works without company access and preserves committed saves and session revocation", async ({
  page,
  context,
}) => {
  const administrator = "a0000000-0000-4000-8000-000000000067";
  const member = "a0000000-0000-4000-8000-000000000068";
  const reviewPath = `/api/v1/identity/accounts/${member}`;
  await signInAndEnrollAuthenticator(page, "browser-platform-admin@example.invalid");
  await expect(page.getByRole("heading", { name: "No company access", exact: true })).toBeVisible();
  await page.getByRole("link", { name: "Platform accounts", exact: true }).click();
  await page
    .getByRole("searchbox", { name: "Find an account", exact: true })
    .fill("browser-platform-member@example.invalid");
  await page.getByRole("button", { name: "Search", exact: true }).click();
  await page
    .getByRole("button", { name: "View account: Browser Platform Member", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Browser Platform Member", exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Edit account access", exact: true }).click();
  await expect(page.getByText("Reviewed version: 0", { exact: true })).toBeVisible();
  await page.getByRole("checkbox", { name: "Account active", exact: true }).uncheck();
  await page.getByRole("checkbox", { name: "Create companies", exact: true }).check();
  await page.getByLabel("Reason", { exact: true }).fill("Pause the reviewed fixture account");

  const commands: { operation: string | undefined; body: unknown }[] = [];
  await page.route(`**${reviewPath}/access`, async (route) => {
    if (route.request().method() !== "PUT") return route.fallback();
    commands.push({
      operation: route.request().headers()["idempotency-key"],
      body: route.request().postDataJSON(),
    });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    if (commands.length === 1) return route.abort("failed");
    return route.fulfill({ response });
  });
  await page.getByRole("button", { name: "Save access", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save outcome is unconfirmed.");
  await page.getByRole("button", { name: "Check save outcome", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Account access saved.");
  expect(commands).toHaveLength(2);
  expect(commands[0]?.operation).toMatch(/^[0-9a-f-]{36}$/u);
  expect(commands[0]).toEqual(commands[1]);
  expect(commands[0]?.body).toEqual({
    expectedVersion: 0,
    active: false,
    platformPermissions: ["companies.create"],
    reason: "Pause the reviewed fixture account",
  });
  const retained = await context.request.get(reviewPath);
  expect(retained.status()).toBe(200);
  expect(await retained.json()).toMatchObject({
    account: { id: member, active: false, version: 1, platformPermissions: ["companies.create"] },
  });
  await page.unroute(`**${reviewPath}/access`);

  await page.getByRole("link", { name: "View account", exact: true }).click();
  await page.getByRole("link", { name: "Edit account access", exact: true }).click();
  await expect(page.getByText("Reviewed version: 1", { exact: true })).toBeVisible();
  await page.getByRole("checkbox", { name: "Account active", exact: true }).check();
  await page.getByLabel("Reason", { exact: true }).fill("Reactivate the reviewed fixture account");
  await page.getByRole("button", { name: "Save access", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Account access saved.");
  const reactivated = await context.request.get(reviewPath);
  expect(reactivated.status()).toBe(200);
  expect(await reactivated.json()).toMatchObject({
    account: { id: member, active: true, version: 2, platformPermissions: ["companies.create"] },
  });

  await page.goto(`/platform/accounts/${administrator}/edit`);
  await expect(page.getByRole("checkbox", { name: "Account active", exact: true })).toBeDisabled();
  await expect(
    page.getByRole("checkbox", { name: "Manage platform accounts", exact: true }),
  ).toBeDisabled();
  await page.getByRole("checkbox", { name: "Create companies", exact: true }).uncheck();
  await page
    .getByLabel("Reason", { exact: true })
    .fill("Retain only platform account administration");
  await page.getByRole("button", { name: "Save access", exact: true }).click();
  await expect(page.getByRole("button", { name: "Sign in", exact: true })).toBeVisible();
  expect((await context.request.get("/api/v1/me")).status()).toBe(401);
});
