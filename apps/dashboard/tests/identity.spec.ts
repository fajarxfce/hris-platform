import { expect, test } from "@playwright/test";
import { companyIds, installIdentityApi } from "./identity-api";

test("sign-in uses current CSRF, accessible password control, company scope and cleared secrets", async ({
  page,
}) => {
  const api = await installIdentityApi(page);
  await page.goto("/");
  await page.getByLabel("Email", { exact: true }).fill("reviewer@example.invalid");
  await page.getByLabel("Password", { exact: true }).fill("Fixture password only");
  await page.getByRole("button", { name: "Show password" }).click();
  await expect(page.getByLabel("Password", { exact: true })).toHaveAttribute("type", "text");
  await page.getByRole("button", { name: "Hide password" }).press("Enter");
  await expect(page.getByLabel("Password", { exact: true })).toHaveAttribute("type", "password");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Overview" })).toBeVisible();
  await expect(page.getByRole("main")).toContainText("North Company");
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  await expect(page.getByRole("main")).toContainText("South Company");
  await expect(page.getByRole("main")).not.toContainText("North Company");
  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
  await expect(page.getByLabel("Password", { exact: true })).toHaveValue("");
  expect(api.commands.map((command) => command.path)).toEqual([
    "/api/v1/auth/login",
    "/api/v1/auth/logout",
  ]);
  expect(api.commands[0]?.csrf).not.toBe(api.commands[1]?.csrf);
  expect(api.unhandled).toEqual([]);
});

test("errors are translated by code without exposing backend detail", async ({ page }) => {
  const api = await installIdentityApi(page, { failLogin: "invalid_credentials" });
  await page.goto("/");
  await page.getByRole("combobox", { name: "Language" }).selectOption("id");
  await page.getByLabel("Email", { exact: true }).fill("reviewer@example.invalid");
  await page.getByLabel("Password", { exact: true }).fill("Incorrect fixture password");
  await page.getByRole("button", { name: "Masuk", exact: true }).click();
  await expect(page.getByText("Email atau password tidak sesuai.", { exact: true })).toBeVisible();
  await expect(page.getByLabel("Password", { exact: true })).toHaveValue("");
  await expect(page.locator("body")).not.toContainText("INTERNAL TECHNICAL");
  await page.getByRole("combobox", { name: "Bahasa" }).selectOption("en");
  await expect(
    page.getByText("The email or password is incorrect.", { exact: true }),
  ).toBeVisible();
  expect(api.commands).toHaveLength(1);
  expect(api.unhandled).toEqual([]);
});

test("authenticator enrollment retains one-use recovery codes until acknowledgement", async ({
  page,
}) => {
  const api = await installIdentityApi(page, { signedIn: true, mfa: true });
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Account verification" })).toBeVisible();
  await page.getByRole("button", { name: "Set up an authenticator" }).click();
  await expect(page.getByText("JBSWY3DPEHPK3PXP", { exact: true })).toBeVisible();
  await page.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await page.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Recovery codes" })).toBeVisible();
  for (const code of api.recoveryCodes)
    await expect(page.getByText(code, { exact: true })).toBeVisible();
  await expect(page.getByText("JBSWY3DPEHPK3PXP", { exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await expect(page.getByRole("button", { name: "Theme", exact: true })).toHaveText("Light");
  await page.getByRole("button", { name: "I have saved the codes" }).click();
  await expect(page.getByRole("heading", { name: "Overview" })).toBeVisible();
  for (const code of api.recoveryCodes)
    await expect(page.getByText(code, { exact: true })).toHaveCount(0);
  expect(api.commands).toHaveLength(2);
  expect(api.unhandled).toEqual([]);
});

test("company selection can cancel a pending response without restoring obsolete content", async ({
  page,
}) => {
  const api = await installIdentityApi(page, { signedIn: true });
  let release!: () => void;
  const held = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route(`**/api/v1/companies/${companyIds[1]}/me/access`, async (route) => {
    await held;
    await route.fulfill({ json: { companyId: companyIds[1], permissions: ["company.read"] } });
  });
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Overview" })).toBeVisible();
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  await expect(page.getByRole("heading", { name: "Overview" })).toHaveCount(0);
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[0]);
  release();
  await expect(page.getByRole("main")).toContainText("North Company");
  await expect(page.getByRole("main")).not.toContainText("South Company");
  expect(api.unhandled).toEqual([]);
});

test("the responsive shell offers keyboard navigation and an honest empty company state", async ({
  page,
}) => {
  const api = await installIdentityApi(page, { signedIn: true, noCompanies: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "No company access" })).toBeVisible();
  await expect(page.getByRole("combobox", { name: "Company", exact: true })).toBeDisabled();
  await page.keyboard.press("Tab");
  await expect(page.getByRole("button", { name: "Skip to content" })).toBeFocused();
  const currentUrl = page.url();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("main")).toBeFocused();
  await expect(page).toHaveURL(currentUrl);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  expect(api.unhandled).toEqual([]);
});
