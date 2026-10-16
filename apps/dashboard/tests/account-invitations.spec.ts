import { expect, type Page, test } from "@playwright/test";
import { installAccountInvitationsApi } from "./account-invitations-api";
import { managedAccount, managedAccountId } from "./fixtures/managed-accounts";

async function fillInvitation(page: Page) {
  await page.getByLabel("Name", { exact: true }).fill("Owned Colleague");
  await page.getByLabel("Email", { exact: true }).fill("owned@example.test");
  await page.getByLabel("Reason", { exact: true }).fill("New colleague account");
}
test("creates an inactive invitation without granting company access", async ({ page }) => {
  const f = await installAccountInvitationsApi(page);
  await page.goto("/platform/accounts");
  await page.getByRole("link", { name: "Invite account", exact: true }).click();
  await fillInvitation(page);
  await page.screenshot({ path: "../../.work/dashboard-account-invitation.png", fullPage: true });
  await page.getByRole("button", { name: "Send invitation", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Invitation queued for delivery.");
  expect(f.invitationWrites).toHaveLength(1);
  expect(f.invitationWrites[0]?.body).toMatchObject({
    email: "owned@example.test",
    displayName: "Owned Colleague",
    reason: "New colleague account",
    expectedVersion: null,
  });
  expect(f.identity.reads.access).toBe(0);
  await page.getByRole("link", { name: "View account", exact: true }).click();
  await expect(page.getByText("Invitation pending", { exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Send new invitation", exact: true })).toBeVisible();
});
test("retains the original invitation through lost response, MFA and later version conflict", async ({
  page,
}) => {
  const f = await installAccountInvitationsApi(page);
  await page.goto("/platform/accounts/new");
  await fillInvitation(page);
  f.dropInvitation();
  await page.getByRole("button", { name: "Send invitation", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The invitation outcome is unconfirmed.");
  f.identity.expireMfa();
  f.rejectInvitation("mfa_required");
  await page.getByRole("button", { name: "Check invitation outcome", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  f.rejectInvitation("stale_version");
  await page.getByRole("button", { name: "Check invitation outcome", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The invitation outcome is unconfirmed.");
  await page.getByRole("button", { name: "Check invitation outcome", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Invitation queued for delivery.");
  expect(f.invitationWrites).toHaveLength(4);
  for (const request of f.invitationWrites) expect(request).toEqual(f.invitationWrites[0]);
  expect(f.invitationReceipts.size).toBe(1);
});
test("resend pins the recipient and requires review after a concurrent update", async ({
  page,
}) => {
  const f = await installAccountInvitationsApi(page);
  f.records.set(managedAccountId(), {
    ...managedAccount(),
    active: false,
    invitationPending: true,
  });
  await page.goto(`/platform/accounts/${managedAccountId()}/invite`);
  await expect(page.getByLabel("Email", { exact: true })).toHaveAttribute("readonly", "");
  await page.getByLabel("Reason", { exact: true }).fill("Renew invitation");
  f.records.set(managedAccountId(), {
    ...managedAccount(),
    active: false,
    invitationPending: true,
    version: 5,
  });
  await page.getByRole("button", { name: "Send invitation", exact: true }).click();
  await expect(page.getByRole("button", { name: "Send invitation", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Review current version", exact: true }).click();
  await page.getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByText("Reviewed version: 5", { exact: true })).toBeVisible();
  await page.getByLabel("Reason", { exact: true }).fill("Renew reviewed invitation");
  await page.getByRole("button", { name: "Send invitation", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Invitation queued for delivery.");
  expect(f.invitationWrites[1]?.body).toMatchObject({
    id: managedAccountId(),
    email: managedAccount().email,
    expectedVersion: 5,
  });
});
test("disabled mail is a definite rejection and does not claim delivery", async ({ page }) => {
  const f = await installAccountInvitationsApi(page);
  await page.goto("/platform/accounts/new");
  await fillInvitation(page);
  f.rejectInvitation("mail_not_configured");
  await page.getByRole("button", { name: "Send invitation", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Email delivery is not configured.");
  await expect(page.getByRole("button", { name: "Send invitation", exact: true })).toBeEnabled();
  expect(f.invitationReceipts.size).toBe(0);
  await page.getByRole("button", { name: "Send invitation", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Invitation queued for delivery.");
  expect(f.invitationWrites[1]?.operation).not.toBe(f.invitationWrites[0]?.operation);
});
test("pending invitation guards navigation and blocks duplicate clicks", async ({ page }) => {
  const f = await installAccountInvitationsApi(page);
  await page.goto("/platform/accounts/new");
  await fillInvitation(page);
  const held = f.holdInvitation();
  try {
    await page.getByRole("button", { name: "Send invitation", exact: true }).click();
    await held.entered;
    await expect(page.getByRole("button", { name: "Send invitation", exact: true })).toBeDisabled();
    await page.getByRole("link", { name: "Back to accounts", exact: true }).click();
    await page.getByRole("button", { name: "Stay on this page", exact: true }).click();
  } finally {
    held.release();
  }
  await expect(page.getByRole("status")).toContainText("Invitation queued for delivery.");
  expect(f.invitationWrites).toHaveLength(1);
});
test("invitation remains readable in dark mode at 390 pixels with doubled Indonesian text", async ({
  page,
}) => {
  await installAccountInvitationsApi(page);
  await page.goto("/platform/accounts/new");
  await fillInvitation(page);
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => {
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
    scrollTo(0, 0);
  });
  await expect(page.getByLabel("Nama", { exact: true })).toHaveValue("Owned Colleague");
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: "../../.work/dashboard-account-invitation-mobile.png",
    fullPage: true,
  });
});
