import { expect, type Page, test } from "@playwright/test";
import {
  managedAccount,
  managedAccountId,
  platformActorId,
  platformPermissions,
} from "./fixtures/managed-accounts";
import { installManagedAccountsApi } from "./managed-accounts-api";

async function edit(page: Page, id = managedAccountId()) {
  await page.goto(`/platform/accounts/${id}/edit`);
  await expect(page.getByRole("checkbox", { name: "Account active", exact: true })).toBeVisible();
}
async function disable(page: Page) {
  await page.getByRole("checkbox", { name: "Account active", exact: true }).uncheck();
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed account access");
}

test("platform administrators without a company can search explicit bounded account pages", async ({
  page,
}) => {
  const f = await installManagedAccountsApi(page, { longPage: true, noCompanies: true });
  await page.goto("/platform/accounts");
  await expect(
    page.getByRole("button", { name: "View account: Example Colleague 1", exact: true }),
  ).toBeVisible();
  expect(f.identity.reads.access).toBe(0);
  expect(f.reads).toHaveLength(1);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "View account: Example Colleague 51", exact: true }),
  ).toBeVisible();
  expect(f.reads[1]?.searchParams.get("after")).toBe(managedAccountId(50));
  await page.getByRole("searchbox", { name: "Find an account", exact: true }).fill("Colleague 1");
  expect(f.reads).toHaveLength(2);
  await page.getByRole("button", { name: "Search", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "View account: Example Colleague 1", exact: true }),
  ).toBeVisible();
  expect(f.reads).toHaveLength(3);
  expect(f.reads[2]?.searchParams.get("query")).toBe("colleague 1");
  expect(f.reads[2]?.searchParams.has("after")).toBe(false);
  await page.screenshot({ path: "../../.work/dashboard-platform-accounts.png", fullPage: true });
  await page
    .getByRole("button", { name: "View account: Example Colleague 1", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Example Colleague 1", exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Back to accounts", exact: true }).click();
  await expect(page.getByRole("searchbox", { name: "Find an account", exact: true })).toHaveValue(
    "Colleague 1",
  );
  expect(f.identity.unhandled).toEqual([]);
});

test("company-only administrators cannot acquire or navigate platform account data", async ({
  page,
}) => {
  const f = await installManagedAccountsApi(page, { allowed: false });
  await page.goto("/platform/accounts");
  await expect(page.getByRole("alert")).toContainText(
    "This action requires platform administration access.",
  );
  await expect(page.getByRole("link", { name: "Platform accounts", exact: true })).toHaveCount(0);
  expect(f.reads).toEqual([]);
  await page.goto(`/platform/accounts/${managedAccountId()}/edit`);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(f.reads).toEqual([]);
  expect(f.writes).toEqual([]);
});

test("account editing displays session effects and saves an observed change", async ({ page }) => {
  const f = await installManagedAccountsApi(page);
  await edit(page);
  await expect(page.getByRole("button", { name: "Save access", exact: true })).toBeDisabled();
  await page.getByLabel("Reason", { exact: true }).fill("Only a reason is not an access change");
  await expect(page.getByRole("button", { name: "Save access", exact: true })).toBeDisabled();
  await disable(page);
  await expect(
    page.getByText("Saving ends this account's sessions. The user must sign in again.", {
      exact: true,
    }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Save access", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Account access saved.");
  expect(f.writes).toHaveLength(1);
  expect(f.writes[0]?.body).toEqual({
    expectedVersion: 4,
    active: false,
    platformPermissions: ["companies.create"],
    reason: "Reviewed account access",
  });
  await page.getByRole("link", { name: "View account", exact: true }).click();
  await expect(page.getByText("Inactive", { exact: true })).toBeVisible();
});

test("lost account-save responses keep their original command through MFA and later conflicts", async ({
  page,
}) => {
  const f = await installManagedAccountsApi(page);
  await edit(page);
  await disable(page);
  f.dropNextSave();
  await page.getByRole("button", { name: "Save access", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save outcome is unconfirmed.");
  f.identity.expireMfa();
  f.rejectNextSave("mfa_required");
  await page.getByRole("button", { name: "Check save outcome", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  f.rejectNextSave("stale_version");
  await page.getByRole("button", { name: "Check save outcome", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save outcome is unconfirmed.");
  await page.getByRole("button", { name: "Check save outcome", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Account access saved.");
  expect(f.writes).toHaveLength(4);
  for (const request of f.writes) expect(request).toEqual(f.writes[0]);
  expect(f.records.get(managedAccountId())?.version).toBe(5);
});

test("a definite version conflict requires protected reloading before another command", async ({
  page,
}) => {
  const f = await installManagedAccountsApi(page);
  await edit(page);
  await disable(page);
  f.records.set(managedAccountId(), { ...managedAccount(), version: 5 });
  await page.getByRole("button", { name: "Save access", exact: true }).click();
  await page.getByRole("button", { name: "Review current version", exact: true }).click();
  await page.getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByText("Reviewed version: 5", { exact: true })).toBeVisible();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  await disable(page);
  await page.getByRole("button", { name: "Save access", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Account access saved.");
  expect(f.writes).toHaveLength(2);
  expect(f.writes[1]?.operation).not.toBe(f.writes[0]?.operation);
  expect(f.writes[1]?.body.expectedVersion).toBe(5);
});

test("pending account saves disable editing and guard navigation", async ({ page }) => {
  const f = await installManagedAccountsApi(page);
  await edit(page);
  await disable(page);
  const held = f.holdSave();
  try {
    await page.getByRole("button", { name: "Save access", exact: true }).click();
    await held.entered;
    await expect(
      page.getByRole("checkbox", { name: "Account active", exact: true }),
    ).toBeDisabled();
    await expect(page.getByLabel("Reason", { exact: true })).toHaveAttribute("readonly", "");
    await page.getByRole("link", { name: "Back to account", exact: true }).click();
    await page.getByRole("button", { name: "Stay on this page", exact: true }).click();
  } finally {
    held.release();
  }
  await expect(page.getByRole("status")).toContainText("Account access saved.");
  expect(f.writes).toHaveLength(1);
});

test("self-access changes protect administrator status and revalidate the revoked session", async ({
  page,
}) => {
  const f = await installManagedAccountsApi(page, { noCompanies: true });
  f.records.set(platformActorId, {
    ...managedAccount(),
    id: platformActorId,
    displayName: "Sample Reviewer",
    email: "reviewer@example.invalid",
    platformPermissions,
  });
  await edit(page, platformActorId);
  await expect(page.getByRole("checkbox", { name: "Account active", exact: true })).toBeDisabled();
  await expect(
    page.getByRole("checkbox", { name: "Manage platform accounts", exact: true }),
  ).toBeDisabled();
  await page.getByRole("checkbox", { name: "Create companies", exact: true }).uncheck();
  await page.getByLabel("Reason", { exact: true }).fill("Retain account administration only");
  await page.getByRole("button", { name: "Save access", exact: true }).click();
  await expect(page.getByRole("button", { name: "Sign in", exact: true })).toBeVisible();
  expect(f.writes).toHaveLength(1);
  expect(f.identity.reads.access).toBe(0);
});

test("pending invitations cannot be activated and their cancellation is explicit", async ({
  page,
}) => {
  const f = await installManagedAccountsApi(page);
  f.records.set(managedAccountId(), {
    ...managedAccount(),
    active: false,
    invitationPending: true,
  });
  await edit(page);
  await expect(page.getByRole("checkbox", { name: "Account active", exact: true })).toBeDisabled();
  await expect(
    page.getByText(
      "The invitation must be accepted before activation. Saving the account as inactive cancels its pending invitation.",
      { exact: true },
    ),
  ).toBeVisible();
  await page.getByLabel("Reason", { exact: true }).fill("Cancel the reviewed invitation");
  await page.getByRole("button", { name: "Save access", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Account access saved.");
  expect(f.records.get(managedAccountId())).toMatchObject({
    active: false,
    invitationPending: false,
    version: 5,
  });
});

test("revoked platform access clears private data and revalidates without a loop", async ({
  page,
}) => {
  const f = await installManagedAccountsApi(page);
  await page.goto("/platform/accounts");
  await expect(
    page.getByRole("button", { name: "View account: Example Colleague 1", exact: true }),
  ).toBeVisible();
  const sessions = f.identity.reads.session;
  f.identity.setPlatformPermissions([]);
  f.failReads("platform_administrator_required");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText(
    "This action requires platform administration access.",
  );
  await expect(page.getByRole("link", { name: "Platform accounts", exact: true })).toHaveCount(0);
  await expect(page.getByText("Example Colleague 1", { exact: true })).toHaveCount(0);
  expect(f.identity.reads.session).toBe(sessions + 1);
  expect(f.reads).toHaveLength(2);
});

test("leaving an account review cancels the read and rejects a late response", async ({ page }) => {
  const f = await installManagedAccountsApi(page);
  await page.goto("/platform/accounts");
  await expect(
    page.getByRole("button", { name: "View account: Example Colleague 1", exact: true }),
  ).toBeVisible();
  const held = f.holdRead();
  try {
    await page
      .getByRole("button", { name: "View account: Example Colleague 1", exact: true })
      .click();
    await held.entered;
    await page.getByRole("link", { name: "Overview", exact: true }).click();
  } finally {
    held.release();
  }
  await expect(page).toHaveURL(/\/$/u);
  await expect(page.getByRole("heading", { name: "Example Colleague 1", exact: true })).toHaveCount(
    0,
  );
});

test("account editing remains readable in Indonesian dark appearance with doubled text", async ({
  page,
}) => {
  const f = await installManagedAccountsApi(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await edit(page);
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.getByRole("checkbox", { name: "Akun aktif", exact: true }).uncheck();
  await page.getByLabel("Alasan", { exact: true }).fill("Peninjauan akses akun");
  await page.evaluate(() => {
    const measurements = Array.from(document.querySelectorAll<HTMLElement>("main *"), (element) => {
      const style = getComputedStyle(element);
      return {
        element,
        size: Number.parseFloat(style.fontSize),
        lineHeight: Number.parseFloat(style.lineHeight),
      };
    });
    for (const { element, size, lineHeight } of measurements) {
      element.style.fontSize = `${size * 2}px`;
      if (Number.isFinite(lineHeight)) element.style.lineHeight = `${lineHeight * 2}px`;
    }
  });
  await expect(page.getByRole("button", { name: "Simpan akses", exact: true })).toBeEnabled();
  const width = await page.evaluate(() => ({
    page: document.documentElement.scrollWidth,
    viewport: innerWidth,
  }));
  expect(width.page).toBeLessThanOrEqual(width.viewport);
  await page.screenshot({
    path: "../../.work/dashboard-platform-account-mobile.png",
    fullPage: true,
  });
  expect(f.reads).toHaveLength(1);
});
