import { randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import { expect, type Page, test } from "@playwright/test";
import { signInAndEnrollAuthenticator } from "../integration/fixtures/sign-in-and-enroll-authenticator";

type Mail = { recipient: string; subject: string; text: string };
async function messagesFor(email: string): Promise<Mail[]> {
  const messages = JSON.parse(
    await readFile(
      new URL("../../../.work/worker-integration/mail.json", import.meta.url),
      "utf-8",
    ),
  ) as Mail[];
  return messages.filter((message) => message.recipient === email);
}
async function deliveredLink(email: string, count: number, path: string): Promise<string> {
  await expect.poll(async () => (await messagesFor(email)).length, { timeout: 20_000 }).toBe(count);
  const message = (await messagesFor(email))[count - 1];
  const link = message?.text
    .split(/\r?\n/u)
    .find((line) => line.startsWith("http://127.0.0.1:4175/auth/"));
  expect(link).toBeTruthy();
  const url = new URL(link as string);
  expect(url.origin).toBe("http://127.0.0.1:4175");
  expect(url.pathname).toBe(path);
  expect(url.search).toBe("");
  expect(url.hash).toMatch(/^#token=[A-Za-z0-9_-]{43}$/u);
  return url.href;
}
async function savePassword(page: Page, value: string) {
  await page.getByLabel("New password", { exact: true }).fill(value);
  await page.getByLabel("Confirm password", { exact: true }).fill(value);
  await page.getByRole("button", { name: "Save password", exact: true }).click();
}
async function signIn(page: Page, email: string, password: string) {
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.getByRole("heading", { name: "No company access", exact: true })).toBeVisible();
}

test("real worker delivery, explicit resend and one-use recovery preserve account and session boundaries", async ({
  page,
  context,
  browser,
  request,
}) => {
  await signInAndEnrollAuthenticator(page, "browser-invitation-admin@example.invalid");
  const email = `invite-${randomUUID()}@example.test`;
  const commands: {
    operation: string | undefined;
    body: { id: string; expectedVersion: number | null };
  }[] = [];
  let dropped = false;
  await page.route("**/api/v1/identity/invitations", async (route) => {
    commands.push({
      operation: route.request().headers()["idempotency-key"],
      body: route.request().postDataJSON(),
    });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    if (!dropped) {
      dropped = true;
      await response.dispose();
      return route.abort("failed");
    }
    return route.fulfill({ response });
  });
  await page.goto("/platform/accounts/new");
  await page.getByLabel("Name", { exact: true }).fill("Owned invitation recipient");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Reason", { exact: true }).fill("Provision a disposable test account");
  await page.getByRole("button", { name: "Send invitation", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The invitation outcome is unconfirmed.");
  await page.getByRole("button", { name: "Check invitation outcome", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Invitation queued for delivery.");
  expect(commands).toHaveLength(2);
  expect(commands[1]).toEqual(commands[0]);
  const target = commands[0]?.body.id;
  expect(target).toBeTruthy();
  const oldLink = await deliveredLink(email, 1, "/auth/accept-invitation");
  await page.unroute("**/api/v1/identity/invitations");
  await page.getByRole("link", { name: "View account", exact: true }).click();
  await page.getByRole("link", { name: "Send new invitation", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Replace the first invitation");
  await page.getByRole("button", { name: "Send invitation", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Invitation queued for delivery.");
  const newLink = await deliveredLink(email, 2, "/auth/accept-invitation");
  expect(newLink).not.toBe(oldLink);

  const visitor = await browser.newContext({ baseURL: "http://127.0.0.1:4175" });
  const recipient = await visitor.newPage();
  let native: { accessToken: string; sessionId: string } | undefined;
  try {
    await recipient.goto(oldLink);
    await savePassword(recipient, "Owned-initial-password-123!");
    await expect(recipient.getByRole("alert")).toContainText(
      "This link is invalid, expired or already used.",
    );
    await recipient.goto(newLink);
    await expect(recipient).toHaveURL(/\/auth\/accept-invitation$/u);
    await savePassword(recipient, "Owned-initial-password-123!");
    await expect(recipient.getByRole("status")).toContainText("Password saved.");
    const reviewed = await context.request.get(`/api/v1/identity/accounts/${target}`);
    expect(reviewed.status()).toBe(200);
    expect(await reviewed.json()).toMatchObject({
      account: {
        id: target,
        active: true,
        invitationPending: false,
        version: 2,
        platformPermissions: [],
      },
    });
    await recipient.getByRole("button", { name: "Sign in", exact: true }).click();
    await signIn(recipient, email, "Owned-initial-password-123!");
    const nativeResponse = await request.post("/api/v1/auth/native/login", {
      headers: { "Idempotency-Key": randomUUID() },
      data: {
        email,
        password: "Owned-initial-password-123!",
        deviceName: "Owned invitation acceptance",
      },
    });
    expect(nativeResponse.status()).toBe(200);
    native = (await nativeResponse.json()).credentials as {
      accessToken: string;
      sessionId: string;
    };
    await recipient.goto("/auth/recover-password");
    await recipient.getByLabel("Email", { exact: true }).fill(email);
    await recipient.getByRole("button", { name: "Send recovery link", exact: true }).click();
    await expect(recipient.getByRole("status")).toContainText(
      "If the account supports password recovery, an email will be sent.",
    );
    const recoveryLink = await deliveredLink(email, 3, "/auth/reset-password");
    let confirmations = 0;
    await recipient.route("**/api/v1/auth/password-recovery/confirm", async (route) => {
      confirmations++;
      const response = await route.fetch();
      expect(response.status()).toBe(204);
      await response.dispose();
      return route.abort("failed");
    });
    await recipient.goto(recoveryLink);
    await savePassword(recipient, "Owned-recovered-password-456!");
    await expect(recipient.getByRole("status")).toContainText(
      "The password change was not confirmed.",
    );
    expect(confirmations).toBe(1);
    expect((await visitor.request.get("/api/v1/me")).status()).toBe(401);
    expect(
      (
        await request.get("/api/v1/me", {
          headers: { Authorization: `Bearer ${native.accessToken}` },
        })
      ).status(),
    ).toBe(401);
    await recipient.getByRole("button", { name: "Sign in", exact: true }).click();
    await signIn(recipient, email, "Owned-recovered-password-456!");
    expect(confirmations).toBe(1);
  } finally {
    if (native)
      await request.delete(`/api/v1/auth/native/sessions/${native.sessionId}`, {
        headers: { Authorization: `Bearer ${native.accessToken}` },
      });
    await visitor.close();
  }
});
