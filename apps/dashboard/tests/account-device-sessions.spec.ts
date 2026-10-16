import { expect, type Page, test } from "@playwright/test";
import { installAccountDeviceSessionsApi } from "./account-device-sessions-api";
import { deviceSessionId } from "./fixtures/device-sessions";
import { managedAccountId } from "./fixtures/managed-accounts";

const path = (index = 1) => `/platform/accounts/${managedAccountId(index)}/sessions`;
const endFirst = (page: Page) => page.getByRole("button", { name: /^End session: HRIS Android,/u });
const dialog = (page: Page) =>
  page.getByRole("dialog", { name: "End this app session?", exact: true });
async function review(page: Page) {
  await endFirst(page).click();
  await expect(dialog(page)).toBeVisible();
}

test("account details lead to scoped sessions and retain the directory return context", async ({
  page,
}) => {
  const f = await installAccountDeviceSessionsApi(page);
  const context = "query=colleague";
  await page.goto(`/platform/accounts/${managedAccountId()}?${context}`);
  await page.getByRole("main").getByRole("link", { name: "App sessions", exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`${path()}\\?${context}$`, "u"));
  await review(page);
  await expect(dialog(page).getByText("Example Colleague 1", { exact: true })).toBeVisible();
  await expect(dialog(page).getByText("colleague1@example.invalid", { exact: true })).toBeVisible();
  await dialog(page).getByRole("button", { name: "Cancel", exact: true }).click();
  await expect(endFirst(page)).toBeFocused();
  expect(f.sessionWrites).toEqual([]);
  expect(f.sessionReads).toEqual([managedAccountId()]);
  expect(f.identity.reads.access).toBe(0);
  await expect(page.getByText("Second account device", { exact: true })).toHaveCount(0);
  await page.screenshot({ path: "../../.work/dashboard-account-app-sessions.png", fullPage: true });
  await page.getByRole("link", { name: "Back to account", exact: true }).click();
  await expect(page).toHaveURL(
    new RegExp(`/platform/accounts/${managedAccountId()}\\?${context}$`, "u"),
  );
  expect(f.identity.unhandled).toEqual([]);
});
test("an explicit retry after response loss ends the original family once", async ({ page }) => {
  const f = await installAccountDeviceSessionsApi(page);
  await page.goto(path());
  await review(page);
  f.dropNext();
  await dialog(page).getByRole("button", { name: "End session", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The session may have ended.");
  await expect(endFirst(page)).toBeDisabled();
  await page.getByRole("button", { name: "Retry ending session", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("This app session has ended.");
  expect(f.sessionWrites).toEqual(
    Array(2).fill({ accountId: managedAccountId(), id: deviceSessionId() }),
  );
  expect(f.audits()).toBe(1);
  expect(f.devices.get(managedAccountId(2))?.size).toBe(1);
  await expect(endFirst(page)).toHaveCount(0);
  await expect(page.getByRole("button", { name: /^End session: HRIS device 2,/u })).toBeVisible();
});
test("refresh reconciles an unknown end result without issuing another command", async ({
  page,
}) => {
  const f = await installAccountDeviceSessionsApi(page);
  await page.goto(path());
  await review(page);
  f.dropNext();
  await dialog(page).getByRole("button", { name: "End session", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The session may have ended.");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("This app session has ended.");
  expect(f.sessionReads).toEqual([managedAccountId(), managedAccountId()]);
  expect(f.sessionWrites).toHaveLength(1);
});
test("pending revocation protects the account return link and prevents duplicate actions", async ({
  page,
}) => {
  const f = await installAccountDeviceSessionsApi(page);
  await page.goto(path());
  await review(page);
  const held = f.holdEnd();
  try {
    await dialog(page).getByRole("button", { name: "End session", exact: true }).click();
    await held.entered;
    await expect(endFirst(page)).toBeDisabled();
    await expect(page.getByRole("button", { name: "Refresh", exact: true })).toBeDisabled();
    await page.getByRole("link", { name: "Back to account", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "Stay on this page", exact: true }),
    ).toBeVisible();
    await page.getByRole("button", { name: "Stay on this page", exact: true }).click();
  } finally {
    held.release();
  }
  await expect(page.getByRole("status")).toContainText("This app session has ended.");
  await expect(page).toHaveURL(new RegExp(`${path()}$`, "u"));
  expect(f.sessionWrites).toHaveLength(1);
});
test("MFA recovery requires a fresh review and never resends a rejected action automatically", async ({
  page,
}) => {
  const f = await installAccountDeviceSessionsApi(page);
  await page.goto(path());
  await review(page);
  f.identity.expireMfa();
  f.rejectEnd("mfa_required");
  await dialog(page).getByRole("button", { name: "End session", exact: true }).click();
  await expect(page.getByRole("table", { name: "Signed-in devices", exact: true })).toHaveCount(0);
  await expect(page.getByText("Example Colleague 1", { exact: true })).toHaveCount(0);
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  expect(f.sessionWrites).toHaveLength(1);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await review(page);
  await dialog(page).getByRole("button", { name: "End session", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("This app session has ended.");
  expect(f.sessionWrites).toHaveLength(2);
  expect(f.audits()).toBe(1);
});
test("readmitting unchanged administration does not cause a failed-read loop", async ({ page }) => {
  const f = await installAccountDeviceSessionsApi(page);
  f.failSessionRead("platform_administrator_required");
  const reacquired = page.waitForResponse(
    (response) => new URL(response.url()).pathname === "/api/v1/me" && f.identity.reads.session > 1,
  );
  await page.goto(path());
  await reacquired;
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("table", { name: "Signed-in devices", exact: true })).toHaveCount(0);
  expect(f.sessionReads).toEqual([managedAccountId()]);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(endFirst(page)).toBeVisible();
  expect(f.sessionReads).toEqual([managedAccountId(), managedAccountId()]);
});
test("withdrawn platform authority removes account and device metadata", async ({ page }) => {
  const f = await installAccountDeviceSessionsApi(page);
  await page.goto(path());
  await expect(endFirst(page)).toBeVisible();
  f.identity.setPlatformPermissions([]);
  f.failSessionRead("platform_administrator_required");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("link", { name: "Platform accounts", exact: true })).toHaveCount(0);
  await expect(page.getByRole("table", { name: "Signed-in devices", exact: true })).toHaveCount(0);
  await expect(page.getByText("Example Colleague 1", { exact: true })).toHaveCount(0);
  expect(f.sessionReads).toHaveLength(2);
  expect(f.sessionWrites).toEqual([]);
});
test("company grants alone cannot read account sessions", async ({ page }) => {
  const f = await installAccountDeviceSessionsApi(page, false);
  await page.goto(path());
  await expect(page.getByRole("alert")).toContainText(
    "This action requires platform administration access.",
  );
  expect(f.reads).toEqual([]);
  expect(f.sessionReads).toEqual([]);
  expect(f.sessionWrites).toEqual([]);
});
test("leaving a pending read aborts it before another account is displayed", async ({ page }) => {
  const f = await installAccountDeviceSessionsApi(page);
  await page.addInitScript(() => {
    const original = window.fetch.bind(window);
    window.fetch = (input, init) => {
      if (String(input).includes("/native-sessions"))
        init?.signal?.addEventListener(
          "abort",
          () => {
            document.documentElement.dataset.sessionReadAborted = "true";
          },
          { once: true },
        );
      return original(input, init);
    };
  });
  const held = f.holdSessionRead();
  try {
    await page.goto(path());
    await held.entered;
    await page.getByRole("link", { name: "Back to account", exact: true }).click();
    await expect
      .poll(() => page.evaluate(() => document.documentElement.dataset.sessionReadAborted))
      .toBe("true");
    await page.getByRole("link", { name: "Back to accounts", exact: true }).click();
    await page
      .getByRole("button", { name: "View account: Example Colleague 2", exact: true })
      .click();
    await page.getByRole("main").getByRole("link", { name: "App sessions", exact: true }).click();
    await expect(
      page.getByRole("button", { name: /^End session: Second account device,/u }),
    ).toBeVisible();
  } finally {
    held.release();
  }
  await expect(page).toHaveURL(new RegExp(`${path(2)}$`, "u"));
  await expect(page.getByText("Example Colleague 1", { exact: true })).toHaveCount(0);
  await expect(endFirst(page)).toHaveCount(0);
  expect(f.sessionReads).toEqual([managedAccountId(), managedAccountId(2)]);
});
test("confirmation supports Indonesian dark mode at narrow width with doubled text", async ({
  page,
}) => {
  await installAccountDeviceSessionsApi(page);
  await page.goto(path());
  await expect(endFirst(page)).toBeVisible();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("button", { name: /^Akhiri sesi: HRIS Android,/u }).click();
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
  const confirmation = page.getByRole("dialog", { name: "Akhiri sesi aplikasi ini?", exact: true });
  await expect(confirmation.getByText("colleague1@example.invalid", { exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: "../../.work/dashboard-account-app-sessions-mobile.png",
    fullPage: true,
  });
  const cancel = confirmation.getByRole("button", { name: "Batal", exact: true });
  await cancel.scrollIntoViewIfNeeded();
  await cancel.click();
  await expect(confirmation).toHaveCount(0);
});
