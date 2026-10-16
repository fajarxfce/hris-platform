import { randomUUID } from "node:crypto";
import { expect, type Page, test } from "@playwright/test";
import { signInAndEnrollAuthenticator } from "./fixtures/sign-in-and-enroll-authenticator";

const administrator = "a0000000-0000-4000-8000-000000000072";
const accountId = "a0000000-0000-4000-8000-000000000073";
const issuer = "https://oidc.example.test";
const accountPath = `/platform/accounts/${accountId}`;
const path = `/api/v1/identity/accounts/${accountId}/oidc`;
async function fillIdentity(page: Page, subject: string) {
  await page.getByLabel("Issuer", { exact: true }).fill(issuer);
  await page.getByLabel("Subject", { exact: true }).fill(subject);
  await page.getByLabel("Reason", { exact: true }).fill("Verified owned provider identity");
}

test("real SSO binding administration retains receipts, activation versions and credential revocation", async ({
  page,
  context,
  browser,
  request,
}) => {
  // Provider transport is exercised in OidcHttpTest; this workflow only administers bindings.
  await context.route("https://oidc.example.test/**", async (route) => {
    await route.abort();
    throw new Error("Binding administration must not contact the identity provider");
  });
  await signInAndEnrollAuthenticator(page, "browser-oidc-admin@example.invalid");
  await page.goto(`${accountPath}/sso`);
  await expect(page.getByText("No SSO bindings yet.", { exact: true })).toBeVisible();

  const visitor = await browser.newContext({ baseURL: "http://127.0.0.1:4174" });
  try {
    const member = await visitor.newPage();
    await member.goto("/");
    await member.getByLabel("Email", { exact: true }).fill("browser-oidc-member@example.invalid");
    await member.getByLabel("Password", { exact: true }).fill("Browser-fixture-password-123!");
    await member.getByRole("button", { name: "Sign in", exact: true }).click();
    await expect(
      member.getByRole("heading", { name: "No company access", exact: true }),
    ).toBeVisible();
    const signedIn = await request.post("/api/v1/auth/native/login", {
      headers: { "Idempotency-Key": randomUUID() },
      data: {
        email: "browser-oidc-member@example.invalid",
        password: "Browser-fixture-password-123!",
        deviceName: "Owned SSO binding acceptance",
      },
    });
    expect(signedIn.status()).toBe(200);
    const native = (await signedIn.json()).credentials as {
      accessToken: string;
      sessionId: string;
    };
    const commands: { id: string; operation: string | undefined; body: unknown }[] = [];
    await page.route(`**${path}/*`, async (route) => {
      if (route.request().method() !== "PUT") return route.fallback();
      commands.push({
        id: new URL(route.request().url()).pathname.split("/").at(-1) ?? "",
        operation: route.request().headers()["idempotency-key"],
        body: route.request().postDataJSON(),
      });
      const response = await route.fetch();
      try {
        expect(response.status()).toBe(200);
        if (commands.length === 1) await route.abort("failed");
        else await route.fulfill({ response });
      } finally {
        await response.dispose();
      }
    });
    await page.getByRole("link", { name: "Add SSO binding", exact: true }).click();
    await fillIdentity(page, "Owned-Exact-Subject");
    await page.getByRole("button", { name: "Save binding", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("The save outcome is unconfirmed.");
    await page.getByRole("button", { name: "Check save outcome", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("SSO binding saved.");
    expect(commands).toHaveLength(2);
    expect(commands[0]).toEqual(commands[1]);
    const id = commands[0]?.id;
    expect(id).toMatch(/^[0-9a-f-]{36}$/u);
    const reviewed = await context.request.get(`${path}/${id}`);
    expect(reviewed.status()).toBe(200);
    expect(await reviewed.json()).toMatchObject({
      id,
      issuer,
      subject: "Owned-Exact-Subject",
      active: true,
      version: 0,
    });
    expect((await visitor.request.get("/api/v1/me")).status()).toBe(401);
    expect(
      (
        await request.get("/api/v1/me", {
          headers: { Authorization: `Bearer ${native.accessToken}` },
        })
      ).status(),
    ).toBe(401);
    await page.unroute(`**${path}/*`);

    await page.getByRole("link", { name: "Review binding", exact: true }).click();
    await expect(page.getByLabel("Subject", { exact: true })).toHaveAttribute("readonly", "");
    await page.getByRole("checkbox", { name: "Binding active", exact: true }).uncheck();
    await page.getByLabel("Reason", { exact: true }).fill("Deactivate the reviewed SSO credential");
    await page.getByRole("button", { name: "Save binding", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("SSO binding saved.");
    const disabled = await context.request.get(`${path}/${id}`);
    expect(disabled.status()).toBe(200);
    expect(await disabled.json()).toMatchObject({ active: false, version: 1 });
    await page.getByRole("button", { name: "Review binding", exact: true }).click();
    await page.getByRole("checkbox", { name: "Binding active", exact: true }).check();
    await page.getByLabel("Reason", { exact: true }).fill("Reactivate verified SSO credential");
    await page.getByRole("button", { name: "Save binding", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("SSO binding saved.");
    const restored = await context.request.get(`${path}/${id}`);
    expect(restored.status()).toBe(200);
    expect(await restored.json()).toMatchObject({ active: true, version: 2 });
    const account = await context.request.get(`/api/v1/identity/accounts/${accountId}`);
    expect(account.status()).toBe(200);
    expect(await account.json()).toMatchObject({
      account: { active: true, version: 3, platformPermissions: [] },
    });

    await page.goto(`/platform/accounts/${administrator}/sso/new`);
    await fillIdentity(page, "Owned-Administrator-Subject");
    await page.getByRole("button", { name: "Save binding", exact: true }).click();
    await expect(page.getByRole("button", { name: "Sign in", exact: true })).toBeVisible();
    expect((await context.request.get("/api/v1/me")).status()).toBe(401);
  } finally {
    // The isolated API fixture removes all native-session records with its database.
    await visitor.close();
  }
});
