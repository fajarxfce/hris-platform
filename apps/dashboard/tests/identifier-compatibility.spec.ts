import { expect, test } from "@playwright/test";
import { installIdentityApi } from "./identity-api";

test("authenticator enrollment works when the browser omits randomUUID", async ({ page }) => {
  await page.addInitScript(() =>
    Object.defineProperty(globalThis.crypto, "randomUUID", {
      value: undefined,
      configurable: true,
    }),
  );
  const api = await installIdentityApi(page, { mfa: true });
  await page.goto("/");
  await page.getByLabel("Email", { exact: true }).fill("reviewer@example.invalid");
  await page.getByLabel("Password", { exact: true }).fill("Fictional-password-123!");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await page.getByRole("button", { name: "Set up an authenticator", exact: true }).click();
  await expect(page.getByLabel("Authenticator code", { exact: true })).toBeVisible();
  expect(
    api.commands.filter((command) => command.path === "/api/v1/auth/mfa/enrollment"),
  ).toHaveLength(1);
  expect(api.unhandled).toEqual([]);
});
