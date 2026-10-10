import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { reviewApprovalReassignment } from "./approval-reassignment";
import { reviewApprovalTemplate } from "./approval-template";
import { authenticatorCode } from "./fixtures/authenticator";

test("real API templates, delegations and expense reassignment recover receipts and respect company scope", async ({
  page,
  context,
}) => {
  await page.goto("/");
  await page.getByLabel("Email", { exact: true }).fill("browser-approval-admin@example.invalid");
  await page.getByLabel("Password", { exact: true }).fill("Browser-fixture-password-123!");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await page.getByRole("button", { name: "Set up an authenticator", exact: true }).click();
  const key = page.locator(".app-enrollment-key code");
  await expect(key).toBeVisible();
  await page
    .getByLabel("Authenticator code", { exact: true })
    .fill(authenticatorCode((await key.textContent()) ?? ""));
  await page.getByRole("button", { name: "Verify", exact: true }).click();
  await page.getByRole("button", { name: "I have saved the codes", exact: true }).click();
  await expect(page.getByRole("heading", { name: "No company access", exact: true })).toBeVisible();
  const csrf = (await (await context.request.get("/api/v1/auth/csrf")).json()) as {
    headerName: string;
    token: string;
  };
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const created = await context.request.post("/api/v1/companies", {
    headers: headers(),
    data: { code: "APPROVALBROWSER", name: "Browser Approvals", timezone: "Asia/Jakarta" },
  });
  expect(created.status()).toBe(200);
  const company = ((await created.json()) as { id: string }).id;
  const second = await context.request.post("/api/v1/companies", {
    headers: headers(),
    data: { code: "APPROVALOTHER", name: "Browser Other", timezone: "Asia/Jakarta" },
  });
  expect(second.status()).toBe(200);
  const otherCompany = ((await second.json()) as { id: string }).id;
  const delegate = "a0000000-0000-4000-8000-000000000002";
  const granted = await context.request.put(`/api/v1/companies/${company}/members/${delegate}`, {
    headers: headers(),
    data: {
      active: true,
      expectedVersion: null,
      permissions: ["approvals.read", "leave.approve", "expenses.approve"],
      reason: "Browser delegation fixture",
    },
  });
  expect(granted.status()).toBe(200);
  await page.reload();
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(company);
  await reviewApprovalTemplate(page, context, company, otherCompany);
  await page.getByRole("link", { name: "My delegations", exact: true }).click();
  await page.getByRole("link", { name: "Create delegation", exact: true }).click();
  await page.getByRole("button", { name: "Select delegate", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Select approver", exact: true })
    .getByRole("button", { name: "Select: Browser Delegate", exact: true })
    .click();
  await page.getByLabel("Reason", { exact: true }).fill("Browser coverage review");
  const commands: { operation: string | undefined; body: unknown }[] = [];
  let committed: { id: string; version: number } | null = null;
  await page.route("**/api/v1/companies/*/approvals/delegations/*", async (route) => {
    if (route.request().method() !== "PUT") return route.continue();
    commands.push({
      operation: route.request().headers()["idempotency-key"],
      body: route.request().postDataJSON(),
    });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    committed = await response.json();
    if (commands.length === 1) return route.abort("failed");
    return route.fulfill({ response });
  });
  await page.getByRole("button", { name: "Save delegation", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save result is unconfirmed");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Delegation saved.");
  expect(commands).toHaveLength(2);
  expect(commands[0]).toEqual(commands[1]);
  expect(committed).toMatchObject({ version: 0 });
  await page.unroute("**/api/v1/companies/*/approvals/delegations/*");
  await page.getByRole("link", { name: "View delegation", exact: true }).click();
  const id = new URL(page.url()).pathname.split("/").at(-1);
  const path = `/api/v1/companies/${company}/approvals/delegations/${id}`;
  const record = await context.request.get(path);
  expect(record.status()).toBe(200);
  expect(await record.json()).toMatchObject({ id, version: 0, active: true, toAccount: delegate });
  expect(
    (
      await context.request.get(`/api/v1/companies/${otherCompany}/approvals/delegations/${id}`)
    ).status(),
  ).toBe(404);
  await reviewApprovalReassignment(page, context, company, delegate);
  await page.getByRole("link", { name: "My delegations", exact: true }).click();
  await page
    .getByRole("table", { name: "My delegations", exact: true })
    .getByRole("button", { name: new RegExp(`${id}$`, "u") })
    .click();
  const revoked = await context.request.put(`/api/v1/companies/${company}/members/${delegate}`, {
    headers: headers(),
    data: {
      active: false,
      expectedVersion: 0,
      permissions: [],
      reason: "Browser access revocation",
    },
  });
  expect(revoked.status()).toBe(200);
  await page.getByRole("link", { name: "Edit delegation", exact: true }).click();
  await page.getByRole("checkbox", { name: "Enabled", exact: true }).uncheck();
  await page.getByLabel("Reason", { exact: true }).fill("Disable after access revocation");
  await page.getByRole("button", { name: "Save delegation", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Delegation saved.");
  expect(await (await context.request.get(path)).json()).toMatchObject({
    version: 1,
    active: false,
    toAccount: delegate,
  });
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Sign in", exact: true })).toBeVisible();
});
