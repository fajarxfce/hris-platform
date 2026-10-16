import { randomUUID } from "node:crypto";
import { expect, type Page, test } from "@playwright/test";

const ownerEmail = "browser-session-owner@example.invalid";
const otherEmail = "browser-session-other@example.invalid";
async function signIn(page: Page, email: string) {
  await page.goto("/");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Password", { exact: true }).fill("Browser-fixture-password-123!");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await page.getByRole("link", { name: "App sessions", exact: true }).click();
}

test("real own-session management revokes only the selected native credentials through response loss and account replacement", async ({
  page,
  context,
  request,
}) => {
  const issued: { sessionId: string; accessToken: string; refreshToken: string }[] = [];
  for (const device of [
    { email: ownerEmail, deviceName: "Owned Android acceptance" },
    { email: ownerEmail, deviceName: "Owned desktop acceptance" },
    { email: otherEmail, deviceName: "Other account acceptance" },
  ]) {
    const response = await request.post("/api/v1/auth/native/login", {
      headers: { "Idempotency-Key": randomUUID() },
      data: { ...device, password: "Browser-fixture-password-123!" },
    });
    try {
      expect(response.status()).toBe(200);
      issued.push((await response.json()).credentials);
    } finally {
      await response.dispose();
    }
  }
  const [phone, desktop, other] = issued;
  if (!phone || !desktop || !other) throw new Error("Owned fixture sessions were not issued");
  await signIn(page, ownerEmail);
  await expect(
    page.getByRole("button", { name: /^End session: Owned Android acceptance,/u }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: /^End session: Owned desktop acceptance,/u }),
  ).toBeVisible();
  await expect(page.getByText("Other account acceptance", { exact: true })).toHaveCount(0);
  const listed = await context.request.get("/api/v1/auth/native/sessions");
  expect(listed.status()).toBe(200);
  const records = (await listed.json()) as { id: string }[];
  expect(new Set(records.map((item) => item.id))).toEqual(
    new Set([phone.sessionId, desktop.sessionId]),
  );
  await listed.dispose();

  const csrfResponse = await context.request.get("/api/v1/auth/csrf");
  const csrf = (await csrfResponse.json()) as { headerName: string; token: string };
  await csrfResponse.dispose();
  const foreign = await context.request.delete(`/api/v1/auth/native/sessions/${other.sessionId}`, {
    headers: { [csrf.headerName]: csrf.token },
  });
  expect(foreign.status()).toBe(404);
  expect(await foreign.json()).toMatchObject({ code: "native_session_not_found" });
  await foreign.dispose();
  const missingCsrf = await context.request.delete(
    `/api/v1/auth/native/sessions/${desktop.sessionId}`,
  );
  expect(missingCsrf.status()).toBe(403);
  await missingCsrf.dispose();

  const revokedIds: string[] = [];
  await page.route("**/api/v1/auth/native/sessions/*", async (route) => {
    if (route.request().method() !== "DELETE") return route.fallback();
    revokedIds.push(new URL(route.request().url()).pathname.split("/").at(-1) ?? "");
    const response = await route.fetch();
    try {
      expect(response.status()).toBe(204);
      if (revokedIds.length === 1) await route.abort("failed");
      else await route.fulfill({ response });
    } finally {
      await response.dispose();
    }
  });
  await page.getByRole("button", { name: /^End session: Owned Android acceptance,/u }).click();
  const confirm = page.getByRole("dialog", { name: "End this app session?", exact: true });
  await confirm.getByRole("button", { name: "End session", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The session may have ended.");
  await page.getByRole("button", { name: "Retry ending session", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("This app session has ended.");
  expect(revokedIds).toEqual([phone.sessionId, phone.sessionId]);
  await expect(
    page.getByRole("button", { name: /^End session: Owned Android acceptance,/u }),
  ).toHaveCount(0);
  await page.unroute("**/api/v1/auth/native/sessions/*");

  for (const [token, expected] of [
    [phone.accessToken, 401],
    [desktop.accessToken, 200],
    [other.accessToken, 200],
  ] as const) {
    const response = await request.get("/api/v1/me", {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(response.status()).toBe(expected);
    await response.dispose();
  }
  const refresh = await request.post("/api/v1/auth/native/refresh", {
    headers: { "Idempotency-Key": randomUUID() },
    data: { refreshToken: phone.refreshToken },
  });
  expect(refresh.status()).toBe(401);
  await refresh.dispose();
  const browserSession = await context.request.get("/api/v1/me");
  expect(browserSession.status()).toBe(200);
  await browserSession.dispose();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(
    page.getByRole("button", { name: /^End session: Owned desktop acceptance,/u }),
  ).toBeVisible();

  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await expect(page.getByRole("button", { name: "Sign in", exact: true })).toBeVisible();
  await signIn(page, otherEmail);
  await expect(
    page.getByRole("button", { name: /^End session: Other account acceptance,/u }),
  ).toBeVisible();
  await expect(page.getByText("Owned desktop acceptance", { exact: true })).toHaveCount(0);
  // The isolated fixture owns and removes all issued credentials with its database.
});
