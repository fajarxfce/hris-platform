import { expect, type Page, test } from "@playwright/test";
import { installDeviceSessionsApi } from "./device-sessions-api";
import { deviceSessionId } from "./fixtures/device-sessions";

const path = "/account/sessions";
const endFirst = (page: Page) => page.getByRole("button", { name: /^End session: HRIS Android,/u });
const confirmation = (page: Page) =>
  page.getByRole("dialog", { name: "End this app session?", exact: true });
async function review(page: Page) {
  await endFirst(page).click();
  await expect(confirmation(page)).toBeVisible();
}

test("own app sessions remain available without company or administration grants", async ({
  page,
}) => {
  const f = await installDeviceSessionsApi(page);
  await page.goto("/");
  await page.getByRole("link", { name: "App sessions", exact: true }).click();
  await expect(page.getByRole("table", { name: "Signed-in devices", exact: true })).toBeVisible();
  expect(f.identity.reads.access).toBe(0);
  expect(f.reads).toHaveLength(1);
  await review(page);
  await expect(confirmation(page).getByText("HRIS Android", { exact: true })).toBeVisible();
  await confirmation(page).getByRole("button", { name: "Cancel", exact: true }).click();
  await expect(confirmation(page)).toHaveCount(0);
  await expect(endFirst(page)).toBeFocused();
  expect(f.writes).toEqual([]);
  await page.screenshot({ path: "../../.work/dashboard-app-sessions.png", fullPage: true });
  await review(page);
  await confirmation(page).getByRole("button", { name: "End session", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("This app session has ended.");
  await expect(endFirst(page)).toHaveCount(0);
  await expect(page.getByRole("button", { name: /^End session: HRIS device 2,/u })).toBeVisible();
  expect(f.writes).toEqual([deviceSessionId()]);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(endFirst(page)).toHaveCount(0);
  await expect(page.getByRole("table", { name: "Signed-in devices", exact: true })).toBeVisible();
  expect(f.identity.unhandled).toEqual([]);
});
test("a dropped acknowledgement retains the exact session for an explicit idempotent retry", async ({
  page,
}) => {
  const f = await installDeviceSessionsApi(page);
  await page.goto(path);
  await review(page);
  f.dropNext();
  await confirmation(page).getByRole("button", { name: "End session", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The session may have ended.");
  await expect(endFirst(page)).toBeDisabled();
  expect(f.writes).toEqual([deviceSessionId()]);
  await page.getByRole("button", { name: "Retry ending session", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("This app session has ended.");
  expect(f.writes).toEqual([deviceSessionId(), deviceSessionId()]);
  expect(f.reads).toHaveLength(1);
});
test("refresh resolves an unknown outcome without repeating its command", async ({ page }) => {
  const f = await installDeviceSessionsApi(page);
  await page.goto(path);
  await review(page);
  f.dropNext();
  await confirmation(page).getByRole("button", { name: "End session", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The session may have ended.");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("This app session has ended.");
  expect(f.reads).toHaveLength(2);
  expect(f.writes).toHaveLength(1);
});
test("pending revocation protects navigation and completing it cancels an obsolete departure", async ({
  page,
}) => {
  const f = await installDeviceSessionsApi(page);
  await page.goto(path);
  await review(page);
  const held = f.holdWrite();
  try {
    await confirmation(page).getByRole("button", { name: "End session", exact: true }).click();
    await held.entered;
    await expect(endFirst(page)).toBeDisabled();
    await expect(page.getByRole("button", { name: "Refresh", exact: true })).toBeDisabled();
    await page.getByRole("link", { name: "Overview", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "Stay on this page", exact: true }),
    ).toBeVisible();
    await page.getByRole("button", { name: "Stay on this page", exact: true }).click();
    await page.getByRole("link", { name: "Overview", exact: true }).click();
  } finally {
    held.release();
  }
  await expect(page.getByRole("status")).toContainText("This app session has ended.");
  await expect(page.getByRole("button", { name: "Stay on this page", exact: true })).toHaveCount(0);
  await expect(page).toHaveURL(new RegExp(`${path}$`, "u"));
  expect(f.writes).toHaveLength(1);
});
test("leaving a held acquisition aborts it before a late response can update another page", async ({
  page,
}) => {
  const f = await installDeviceSessionsApi(page);
  await page.addInitScript(() => {
    const original = window.fetch.bind(window);
    const state = window as typeof window & { ownedSessionReadAborted?: boolean };
    window.fetch = (input, init) => {
      if (String(input).endsWith("/auth/native/sessions"))
        init?.signal?.addEventListener(
          "abort",
          () => {
            state.ownedSessionReadAborted = true;
          },
          { once: true },
        );
      return original(input, init);
    };
  });
  const held = f.holdRead();
  try {
    await page.goto(path);
    await held.entered;
    await page.getByRole("link", { name: "Overview", exact: true }).click();
    await expect
      .poll(() =>
        page.evaluate(
          () =>
            (window as typeof window & { ownedSessionReadAborted?: boolean })
              .ownedSessionReadAborted,
        ),
      )
      .toBe(true);
  } finally {
    held.release();
  }
  await expect(page.getByRole("heading", { name: "No company access", exact: true })).toBeVisible();
  await expect(page.getByRole("table", { name: "Signed-in devices", exact: true })).toHaveCount(0);
});
test("an expired initiating credential ends the dashboard scope without a retry loop", async ({
  page,
}) => {
  const f = await installDeviceSessionsApi(page);
  await page.goto(path);
  await expect(endFirst(page)).toBeVisible();
  f.identity.revoke();
  f.failRead("session_revoked");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("button", { name: "Sign in", exact: true })).toBeVisible();
  await expect(page.getByRole("table", { name: "Signed-in devices", exact: true })).toHaveCount(0);
  expect(f.reads).toHaveLength(2);
});

test("readmitting an unchanged account does not silently repeat a rejected session read", async ({
  page,
}) => {
  const f = await installDeviceSessionsApi(page);
  f.failRead("session_revoked");
  const reacquired = page.waitForResponse(
    (response) => new URL(response.url()).pathname === "/api/v1/me" && f.identity.reads.session > 1,
  );
  await page.goto(path);
  await reacquired;
  await expect(page.getByRole("button", { name: "Refresh", exact: true })).toBeVisible();
  await expect(page.getByRole("alert")).toBeVisible();
  expect(f.reads).toHaveLength(1);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(endFirst(page)).toBeVisible();
  expect(f.reads).toHaveLength(2);
});

test("an explicit departure aborts a pending revocation and a late server commit stays on its original account", async ({
  page,
}) => {
  const f = await installDeviceSessionsApi(page);
  await page.addInitScript(() => {
    const original = window.fetch.bind(window);
    const state = window as typeof window & { ownedSessionWriteAborted?: boolean };
    window.fetch = (input, init) => {
      if (String(input).includes("/auth/native/sessions/") && init?.method === "DELETE")
        init.signal?.addEventListener(
          "abort",
          () => {
            state.ownedSessionWriteAborted = true;
          },
          { once: true },
        );
      return original(input, init);
    };
  });
  await page.goto(path);
  await review(page);
  const held = f.holdWrite();
  try {
    await confirmation(page).getByRole("button", { name: "End session", exact: true }).click();
    await held.entered;
    await page.getByRole("link", { name: "Overview", exact: true }).click();
    await page.getByRole("button", { name: "Leave page", exact: true }).click();
    await expect
      .poll(() =>
        page.evaluate(
          () =>
            (window as typeof window & { ownedSessionWriteAborted?: boolean })
              .ownedSessionWriteAborted,
        ),
      )
      .toBe(true);
  } finally {
    held.release();
  }
  await expect(page.getByRole("heading", { name: "No company access", exact: true })).toBeVisible();
  await expect.poll(() => f.sessions.has(deviceSessionId())).toBe(false);
  await page.getByRole("link", { name: "App sessions", exact: true }).click();
  await expect(page.getByRole("button", { name: /^End session: HRIS device 2,/u })).toBeVisible();
  await expect(endFirst(page)).toHaveCount(0);
  expect(f.writes).toEqual([deviceSessionId()]);
});
test("transient list failures wait for user recovery and display an empty account accurately", async ({
  page,
}) => {
  const f = await installDeviceSessionsApi(page);
  f.failRead("request_failed");
  await page.goto(path);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(f.reads).toHaveLength(1);
  f.sessions.clear();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByText("No active app sessions.", { exact: true })).toBeVisible();
  expect(f.reads).toHaveLength(2);
});
test("session confirmation supports Indonesian dark mode at narrow width and doubled text", async ({
  page,
}) => {
  await installDeviceSessionsApi(page);
  await page.goto(path);
  await review(page);
  await confirmation(page).getByRole("button", { name: "Cancel", exact: true }).click();
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
  await expect(
    page.getByRole("dialog", { name: "Akhiri sesi aplikasi ini?", exact: true }),
  ).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.screenshot({ path: "../../.work/dashboard-app-sessions-mobile.png", fullPage: true });
  const cancel = page
    .getByRole("dialog", { name: "Akhiri sesi aplikasi ini?", exact: true })
    .getByRole("button", { name: "Batal", exact: true });
  await cancel.scrollIntoViewIfNeeded();
  await cancel.click();
  await expect(
    page.getByRole("dialog", { name: "Akhiri sesi aplikasi ini?", exact: true }),
  ).toHaveCount(0);
});
