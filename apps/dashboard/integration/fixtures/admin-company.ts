import { randomUUID } from "node:crypto";
import { type BrowserContext, expect, type Page } from "@playwright/test";
import { authenticatorCode } from "./authenticator";

/** Each caller owns a fixture account, MFA enrollment and a newly created company. */
export async function createAdminCompany(
  page: Page,
  context: BrowserContext,
  email: string,
  code: string,
  name: string,
) {
  await page.goto("/");
  await page.getByLabel("Email", { exact: true }).fill(email);
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
  const response = await context.request.post("/api/v1/companies", {
    headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() },
    data: { code, name, timezone: "Asia/Jakarta" },
  });
  expect(response.status()).toBe(200);
  const company = ((await response.json()) as { id: string }).id;
  await page.reload();
  await expect(page.getByRole("heading", { name: "Overview", exact: true })).toBeVisible();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByRole("heading", { name: "Ringkasan", exact: true })).toBeVisible();
  return { company, csrf };
}
