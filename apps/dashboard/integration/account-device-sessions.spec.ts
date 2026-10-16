import { randomUUID } from "node:crypto";
import { expect, type Page, test } from "@playwright/test";
import { signInAndEnrollAuthenticator } from "./fixtures/sign-in-and-enroll-authenticator";

const adminEmail = "browser-session-admin@example.invalid";
const ownerEmail = "browser-managed-session-owner@example.invalid";
const otherEmail = "browser-managed-session-other@example.invalid";
const ownerId = "a0000000-0000-4000-8000-000000000077";
const otherId = "a0000000-0000-4000-8000-000000000078";
const password = "Browser-fixture-password-123!";

async function signIn(page: Page, email: string) {
  await page.goto("/");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.getByRole("button", { name: "Sign out", exact: true })).toBeVisible();
}

test("real administration ends one app family through response loss while independent sessions retain access", async ({
  page,
  context,
  request,
}) => {
  const issued: { sessionId: string; accessToken: string; refreshToken: string }[] = [];
  for (const device of [
    { email: ownerEmail, deviceName: "Administrative review phone" },
    { email: ownerEmail, deviceName: "Administrative review tablet" },
    { email: otherEmail, deviceName: "Independent account phone" },
  ]) {
    const response = await request.post("/api/v1/auth/native/login", {
      headers: { "Idempotency-Key": randomUUID() },
      data: { ...device, password },
    });
    try {
      expect(response.status()).toBe(200);
      issued.push((await response.json()).credentials);
    } finally {
      await response.dispose();
    }
  }
  const [phone, tablet, other] = issued;
  if (!phone || !tablet || !other) throw new Error("Owned fixture credentials were not issued");

  // This separate request context retains the target's independent web session.
  const ownerCsrfResponse = await request.get("/api/v1/auth/csrf");
  const ownerCsrf = (await ownerCsrfResponse.json()) as { headerName: string; token: string };
  await ownerCsrfResponse.dispose();
  const ownerLogin = await request.post("/api/v1/auth/login", {
    headers: { [ownerCsrf.headerName]: ownerCsrf.token },
    data: { email: ownerEmail, password },
  });
  expect(ownerLogin.status()).toBe(200);
  await ownerLogin.dispose();

  await signInAndEnrollAuthenticator(page, adminEmail);
  await expect(page.getByRole("heading", { name: "No company access", exact: true })).toBeVisible();
  await page.goto(`/platform/accounts/${ownerId}`);
  await page.getByRole("main").getByRole("link", { name: "App sessions", exact: true }).click();
  await expect(
    page.getByRole("button", { name: /^End session: Administrative review phone,/u }),
  ).toBeVisible();
  await expect(page.getByText("Independent account phone", { exact: true })).toHaveCount(0);
  const listed = await context.request.get(`/api/v1/identity/accounts/${ownerId}/native-sessions`);
  expect(listed.status()).toBe(200);
  const records = (await listed.json()) as { id: string }[];
  expect(new Set(records.map((record) => record.id))).toEqual(
    new Set([phone.sessionId, tablet.sessionId]),
  );
  await listed.dispose();

  const csrfResponse = await context.request.get("/api/v1/auth/csrf");
  const csrf = (await csrfResponse.json()) as { headerName: string; token: string };
  await csrfResponse.dispose();
  const foreign = await context.request.delete(
    `/api/v1/identity/accounts/${ownerId}/native-sessions/${other.sessionId}`,
    { headers: { [csrf.headerName]: csrf.token } },
  );
  expect(foreign.status()).toBe(404);
  await foreign.dispose();
  const missingCsrf = await context.request.delete(
    `/api/v1/identity/accounts/${ownerId}/native-sessions/${tablet.sessionId}`,
  );
  expect(missingCsrf.status()).toBe(403);
  await missingCsrf.dispose();

  const ended: string[] = [];
  const pattern = `**/api/v1/identity/accounts/${ownerId}/native-sessions/*`;
  await page.route(pattern, async (route) => {
    if (route.request().method() !== "DELETE") return route.fallback();
    ended.push(new URL(route.request().url()).pathname);
    const response = await route.fetch();
    try {
      expect(response.status()).toBe(204);
      if (ended.length === 1) await route.abort("failed");
      else await route.fulfill({ response });
    } finally {
      await response.dispose();
    }
  });
  await page.getByRole("button", { name: /^End session: Administrative review phone,/u }).click();
  const confirmation = page.getByRole("dialog", { name: "End this app session?", exact: true });
  await expect(confirmation.getByText(ownerEmail, { exact: true })).toBeVisible();
  await confirmation.getByRole("button", { name: "End session", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The session may have ended.");
  await page.getByRole("button", { name: "Retry ending session", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("This app session has ended.");
  expect(ended).toEqual(
    Array(2).fill(`/api/v1/identity/accounts/${ownerId}/native-sessions/${phone.sessionId}`),
  );
  await page.unroute(pattern);

  for (const [token, status] of [
    [phone.accessToken, 401],
    [tablet.accessToken, 200],
    [other.accessToken, 200],
  ] as const) {
    const response = await request.get("/api/v1/me", {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(response.status()).toBe(status);
    await response.dispose();
  }
  const refresh = await request.post("/api/v1/auth/native/refresh", {
    headers: { "Idempotency-Key": randomUUID() },
    data: { refreshToken: phone.refreshToken },
  });
  expect(refresh.status()).toBe(401);
  await refresh.dispose();
  const ownerWeb = await request.get("/api/v1/me");
  expect(ownerWeb.status()).toBe(200);
  expect((await ownerWeb.json()).account.id).toBe(ownerId);
  await ownerWeb.dispose();
  const adminWeb = await context.request.get("/api/v1/me");
  expect(adminWeb.status()).toBe(200);
  await adminWeb.dispose();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(
    page.getByRole("button", { name: /^End session: Administrative review tablet,/u }),
  ).toBeVisible();
  await expect(page.getByText("Administrative review phone", { exact: true })).toHaveCount(0);

  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await expect(page.getByRole("button", { name: "Sign in", exact: true })).toBeVisible();
  await signIn(page, ownerEmail);
  await page.goto(`/platform/accounts/${otherId}/sessions`);
  await expect(page.getByRole("alert")).toContainText(
    "This action requires platform administration access.",
  );
  await expect(page.getByRole("table", { name: "Signed-in devices", exact: true })).toHaveCount(0);
  await page.getByRole("link", { name: "App sessions", exact: true }).click();
  await expect(
    page.getByRole("button", { name: /^End session: Administrative review tablet,/u }),
  ).toBeVisible();
  await expect(page.getByText("Independent account phone", { exact: true })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Back to account", exact: true })).toHaveCount(0);
});
