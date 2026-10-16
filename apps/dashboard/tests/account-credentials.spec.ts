import { expect, type Page, test } from "@playwright/test";
import { installAccountCredentialsApi, ownedCredentialToken } from "./account-credentials-api";

const invitation = `/auth/accept-invitation#token=${ownedCredentialToken}`;
async function password(page: Page) {
  await page.getByLabel("New password", { exact: true }).fill("Owned-password-123!");
  await page.getByLabel("Confirm password", { exact: true }).fill("Owned-password-123!");
}
test("captures and removes the token before interaction, without loading an existing session", async ({
  page,
}) => {
  const f = await installAccountCredentialsApi(page, true);
  await page.goto(invitation);
  await expect(page).toHaveURL(/\/auth\/accept-invitation$/u);
  expect(f.identity.reads).toEqual({ session: 0, access: 0 });
  expect(f.confirmations).toEqual([]);
  expect(
    await page.evaluate(
      () =>
        `${JSON.stringify(history.state)}${JSON.stringify(localStorage)}${JSON.stringify(sessionStorage)}`,
    ),
  ).not.toContain(ownedCredentialToken);
  expect(await page.content()).not.toContain(ownedCredentialToken);
  await password(page);
  await page.getByLabel("Confirm password", { exact: true }).fill("Different-password-123!");
  await page.getByRole("button", { name: "Save password", exact: true }).click();
  await expect(page.getByText("Passwords do not match.", { exact: true })).toBeVisible();
  expect(f.confirmations).toHaveLength(0);
  await page.getByLabel("Confirm password", { exact: true }).fill("Owned-password-123!");
  await page.getByRole("button", { name: "Show password", exact: true }).click();
  await expect(page.getByLabel("New password", { exact: true })).toHaveAttribute("type", "text");
  await page.getByRole("button", { name: "Hide password", exact: true }).press("Enter");
  await page.getByRole("button", { name: "Save password", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Password saved. Sign in to continue.");
  await expect(page.getByLabel("New password", { exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Sign in", exact: true })).toBeVisible();
  expect(f.confirmations).toHaveLength(1);
  expect(f.identity.commands.filter((command) => command.path.endsWith("/logout"))).toHaveLength(1);
});
test("a consumed link cannot set another password and diagnostics remain private", async ({
  page,
}) => {
  const f = await installAccountCredentialsApi(page);
  f.consumed.add(ownedCredentialToken);
  await page.goto(invitation);
  await password(page);
  await page.getByRole("button", { name: "Save password", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText(
    "This link is invalid, expired or already used.",
  );
  await expect(page.getByRole("button", { name: "Save password", exact: true })).toHaveCount(0);
  await expect(page.locator("body")).not.toContainText("OWNED PRIVATE DIAGNOSTIC");
  expect(f.confirmations).toHaveLength(1);
});
test("lost confirmation directs sign-in without replaying its one-use command", async ({
  page,
}) => {
  const f = await installAccountCredentialsApi(page);
  f.dropConfirmation();
  await page.goto(invitation);
  await password(page);
  await page.getByRole("button", { name: "Save password", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The password change was not confirmed.");
  await expect(page.getByRole("button", { name: "Save password", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Sign in", exact: true })).toBeVisible();
  expect(f.confirmations).toHaveLength(1);
});
test("a reload cannot recover the discarded URL token from storage", async ({ page }) => {
  const f = await installAccountCredentialsApi(page);
  await page.goto(invitation);
  await expect(page).toHaveURL(/\/auth\/accept-invitation$/u);
  await page.reload();
  await expect(page.getByRole("alert")).toContainText(
    "This link is invalid, expired or already used.",
  );
  expect(f.confirmations).toHaveLength(0);
  expect(f.identity.reads).toEqual({ session: 0, access: 0 });
});
test("recovery acknowledges an empty 202 without revealing account eligibility", async ({
  page,
}) => {
  const f = await installAccountCredentialsApi(page);
  await page.goto("/");
  await page.getByRole("link", { name: "Forgot password?", exact: true }).click();
  await page.getByLabel("Email", { exact: true }).fill("missing@example.test");
  await page.getByRole("button", { name: "Send recovery link", exact: true }).click();
  await expect(page.getByRole("status")).toContainText(
    "If the account supports password recovery, an email will be sent.",
  );
  expect(f.recoveries).toEqual(["missing@example.test"]);
  await page.goto(`/auth/reset-password#token=${ownedCredentialToken}`);
  await password(page);
  await page.getByRole("button", { name: "Save password", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Password saved.");
  expect(f.confirmations[0]?.path).toBe("/api/v1/auth/password-recovery/confirm");
});
test("public password entry retains input across theme and locale changes without session refresh", async ({
  page,
}) => {
  const f = await installAccountCredentialsApi(page);
  await page.goto(invitation);
  await password(page);
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => {
    window.dispatchEvent(new Event("online"));
    document.dispatchEvent(new Event("visibilitychange"));
    const measurements = Array.from(
      document.querySelectorAll<HTMLElement>("main, main *"),
      (element) => ({
        element,
        font: Number.parseFloat(getComputedStyle(element).fontSize),
        line: Number.parseFloat(getComputedStyle(element).lineHeight),
      }),
    );
    for (const { element, font, line } of measurements) {
      element.style.fontSize = `${font * 2}px`;
      if (Number.isFinite(line)) element.style.lineHeight = `${line * 2}px`;
    }
  });
  await expect(page.getByLabel("Password baru", { exact: true })).toHaveValue(
    "Owned-password-123!",
  );
  expect(f.identity.reads).toEqual({ session: 0, access: 0 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: "../../.work/dashboard-invitation-password-mobile.png",
    fullPage: true,
  });
});
