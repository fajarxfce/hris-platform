import { expect, type Page, test } from "@playwright/test";
import {
  managedAccount,
  managedAccountId,
  platformActorId,
  platformPermissions,
} from "./fixtures/managed-accounts";
import { oidcBinding, oidcBindingId, oidcIssuer } from "./fixtures/oidc-bindings";
import { installOidcBindingsApi } from "./oidc-bindings-api";

const accountPath = `/platform/accounts/${managedAccountId()}`;
const directory = `${accountPath}/sso`;
const editor = `${directory}/${oidcBindingId()}`;
async function fillBinding(page: Page) {
  await page.getByLabel("Issuer", { exact: true }).fill(oidcIssuer);
  await page.getByLabel("Subject", { exact: true }).fill("Case / Exact Subject");
  await page.getByLabel("Reason", { exact: true }).fill("Verified provider account");
}
test("opens account-owned SSO pages and retains both directory cursors", async ({ page }) => {
  const f = await installOidcBindingsApi(page, true);
  await page.goto(`${accountPath}?query=colleague&after=31000000-0000-4000-8000-000000000001`);
  await page.getByRole("link", { name: "SSO bindings", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Review binding: Owned-Subject-1", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await page.getByRole("button", { name: "Review binding: Owned-Subject-51", exact: true }).click();
  await expect(page.getByLabel("Subject", { exact: true })).toHaveValue("Owned-Subject-51");
  await expect(page.getByLabel("Subject", { exact: true })).toHaveAttribute("readonly", "");
  await page.getByRole("link", { name: "Back to SSO", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Review binding: Owned-Subject-51", exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Back to account", exact: true }).click();
  expect(new URL(page.url()).searchParams.get("after")).toBe(
    "31000000-0000-4000-8000-000000000001",
  );
  expect(new URL(page.url()).searchParams.has("bindingAfter")).toBe(false);
  expect(f.oidcReads.some((url) => url.searchParams.get("after") === oidcBindingId(50))).toBe(true);
});
test("creates an exact provider binding and makes issuer and subject immutable on review", async ({
  page,
}) => {
  const f = await installOidcBindingsApi(page);
  f.bindings.clear();
  await page.goto(directory);
  await page.getByRole("link", { name: "Add SSO binding", exact: true }).click();
  await fillBinding(page);
  await page.screenshot({ path: "../../.work/dashboard-sso-binding.png", fullPage: true });
  await page.getByRole("button", { name: "Save binding", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("SSO binding saved.");
  expect(f.oidcWrites).toHaveLength(1);
  expect(f.oidcWrites[0]?.body).toEqual({
    issuer: oidcIssuer,
    subject: "Case / Exact Subject",
    active: true,
    expectedVersion: null,
    reason: "Verified provider account",
  });
  expect(f.identity.reads.access).toBe(0);
  await page.getByRole("link", { name: "Review binding", exact: true }).click();
  await expect(page.getByLabel("Issuer", { exact: true })).toHaveAttribute("readonly", "");
  await expect(page.getByLabel("Subject", { exact: true })).toHaveAttribute("readonly", "");
  await expect(page.getByRole("button", { name: "Save binding", exact: true })).toBeDisabled();
});
test("keeps the original save through response loss, MFA and a later conflict", async ({
  page,
}) => {
  const f = await installOidcBindingsApi(page);
  await page.goto(editor);
  await page.getByRole("checkbox", { name: "Binding active", exact: true }).uncheck();
  await page.getByLabel("Reason", { exact: true }).fill("Disable the reviewed binding");
  f.dropBinding();
  await page.getByRole("button", { name: "Save binding", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save outcome is unconfirmed.");
  f.identity.expireMfa();
  f.rejectBinding("mfa_required");
  await page.getByRole("button", { name: "Check save outcome", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  f.rejectBinding("stale_version");
  await page.getByRole("button", { name: "Check save outcome", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save outcome is unconfirmed.");
  await page.getByRole("button", { name: "Check save outcome", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("SSO binding saved.");
  expect(f.oidcWrites).toHaveLength(4);
  for (const request of f.oidcWrites) expect(request).toEqual(f.oidcWrites[0]);
  expect(f.oidcReceipts.size).toBe(1);
});
test("requires a new review after a concurrent binding change", async ({ page }) => {
  const f = await installOidcBindingsApi(page);
  await page.goto(editor);
  await page.getByRole("checkbox", { name: "Binding active", exact: true }).uncheck();
  await page.getByLabel("Reason", { exact: true }).fill("Disable reviewed binding");
  f.bindings.set(oidcBindingId(), { ...oidcBinding(), accountId: managedAccountId(), version: 4 });
  await page.getByRole("button", { name: "Save binding", exact: true }).click();
  await expect(page.getByRole("button", { name: "Save binding", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Review current version", exact: true }).click();
  await page.getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByRole("checkbox", { name: "Binding active", exact: true })).toBeChecked();
  await page.getByRole("checkbox", { name: "Binding active", exact: true }).uncheck();
  await page.getByLabel("Reason", { exact: true }).fill("Disable latest reviewed binding");
  await page.getByRole("button", { name: "Save binding", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("SSO binding saved.");
  expect(f.oidcWrites[1]?.body.expectedVersion).toBe(4);
  expect(f.oidcWrites[1]?.operation).not.toBe(f.oidcWrites[0]?.operation);
  await page.getByRole("button", { name: "Review binding", exact: true }).click();
  await expect(
    page.getByRole("checkbox", { name: "Binding active", exact: true }),
  ).not.toBeChecked();
  await page.getByRole("checkbox", { name: "Binding active", exact: true }).check();
  await page.getByLabel("Reason", { exact: true }).fill("Reactivate the reviewed binding");
  await page.getByRole("button", { name: "Save binding", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("SSO binding saved.");
  expect(f.oidcWrites[2]?.body).toMatchObject({ expectedVersion: 5, active: true });
});
test("guards pending edits and avoids duplicate submission", async ({ page }) => {
  const f = await installOidcBindingsApi(page);
  await page.goto(editor);
  await page.getByRole("checkbox", { name: "Binding active", exact: true }).uncheck();
  await page.getByLabel("Reason", { exact: true }).fill("Disable binding");
  const held = f.holdBinding();
  try {
    await page.getByRole("button", { name: "Save binding", exact: true }).click();
    await held.entered;
    await expect(page.getByRole("button", { name: "Save binding", exact: true })).toBeDisabled();
    await page.getByRole("link", { name: "Back to SSO", exact: true }).click();
    await page.getByRole("button", { name: "Stay on this page", exact: true }).click();
  } finally {
    held.release();
  }
  await expect(page.getByRole("status")).toContainText("SSO binding saved.");
  expect(f.oidcWrites).toHaveLength(1);
});
test("allows deactivation for an inactive account without offering new bindings", async ({
  page,
}) => {
  const f = await installOidcBindingsApi(page);
  f.records.set(managedAccountId(), { ...managedAccount(), active: false });
  await page.goto(directory);
  await expect(page.getByText("This account is inactive.", { exact: false })).toBeVisible();
  await expect(page.getByRole("link", { name: "Add SSO binding", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Review binding: Owned-Subject-1", exact: true }).click();
  await page.getByRole("checkbox", { name: "Binding active", exact: true }).uncheck();
  await expect(page.getByRole("checkbox", { name: "Binding active", exact: true })).toBeDisabled();
  await page.getByLabel("Reason", { exact: true }).fill("Disable credential for inactive account");
  await page.getByRole("button", { name: "Save binding", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("SSO binding saved.");
});
test("successful self changes revalidate the revoked session", async ({ page }) => {
  const f = await installOidcBindingsApi(page);
  f.records.set(platformActorId, { ...managedAccount(), id: platformActorId, platformPermissions });
  f.bindings.set(oidcBindingId(), { ...oidcBinding(), accountId: platformActorId });
  await page.goto(`/platform/accounts/${platformActorId}/sso/${oidcBindingId()}`);
  await page.getByRole("checkbox", { name: "Binding active", exact: true }).uncheck();
  await page.getByLabel("Reason", { exact: true }).fill("Disable my SSO binding");
  await page.getByRole("button", { name: "Save binding", exact: true }).click();
  await expect(page.getByRole("button", { name: "Sign in", exact: true })).toBeVisible();
  expect(f.oidcWrites).toHaveLength(1);
});
test("leaving a pending binding directory aborts its acquisition before the response arrives", async ({
  page,
}) => {
  const f = await installOidcBindingsApi(page);
  await page.addInitScript(() => {
    const original = window.fetch.bind(window);
    const state = window as typeof window & { ownedSsoAborted?: boolean };
    window.fetch = (input, init) => {
      if (String(input).includes("/oidc?"))
        init?.signal?.addEventListener(
          "abort",
          () => {
            state.ownedSsoAborted = true;
          },
          { once: true },
        );
      return original(input, init);
    };
  });
  const held = f.holdBindingRead();
  try {
    await page.goto(directory);
    await held.entered;
    await page.getByRole("link", { name: "Back to account", exact: true }).click();
    await expect
      .poll(() =>
        page.evaluate(
          () => (window as typeof window & { ownedSsoAborted?: boolean }).ownedSsoAborted,
        ),
      )
      .toBe(true);
  } finally {
    held.release();
  }
  await expect(page).toHaveURL(new RegExp(`${accountPath}$`, "u"));
  await expect(page.getByRole("table", { name: "Binding directory", exact: true })).toHaveCount(0);
});
test("editor remains readable in Indonesian dark mode at 390 pixels and doubled text", async ({
  page,
}) => {
  const f = await installOidcBindingsApi(page);
  f.bindings.clear();
  await page.goto(`${directory}/new`);
  await fillBinding(page);
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => {
    const sizes = Array.from(document.querySelectorAll<HTMLElement>("main, main *"), (element) => ({
      element,
      font: Number.parseFloat(getComputedStyle(element).fontSize),
      line: Number.parseFloat(getComputedStyle(element).lineHeight),
    }));
    for (const { element, font, line } of sizes) {
      element.style.fontSize = `${font * 2}px`;
      if (Number.isFinite(line)) element.style.lineHeight = `${line * 2}px`;
    }
    scrollTo(0, 0);
  });
  await expect(page.getByLabel("Subject", { exact: true })).toHaveValue("Case / Exact Subject");
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.screenshot({ path: "../../.work/dashboard-sso-binding-mobile.png", fullPage: true });
});
