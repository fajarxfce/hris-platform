import { expect, type Page, test } from "@playwright/test";
import { deliveryDetail } from "../src/features/communications/di/announcement-delivery-fixture";
import { announcementId, installAnnouncementsApi } from "./announcements-api";
import { companyIds } from "./identity-api";

const path = `/communications/announcements/${announcementId}`;
async function install(page: Page) {
  const api = await installAnnouncementsApi(page);
  const requests: string[] = [];
  let response = deliveryDetail();
  let failure: string | null = null;
  let held: { entered: () => void; wait: Promise<void> } | null = null;
  await page.route("**/api/v1/companies/*/announcements/*/delivery", async (route) => {
    requests.push(route.request().url());
    const snapshot = response;
    const hold = held;
    held = null;
    if (hold) {
      hold.entered();
      await hold.wait;
    }
    if (failure) return route.fulfill({ status: 403, json: { code: failure } });
    return route.fulfill({ json: snapshot });
  });
  return {
    ...api,
    requests,
    set: (next: ReturnType<typeof deliveryDetail>) => {
      response = next;
    },
    fail: (code: string) => {
      failure = code;
    },
    hold: () => {
      let entered!: () => void, release!: () => void;
      const started = new Promise<void>((done) => {
        entered = done;
      });
      const wait = new Promise<void>((done) => {
        release = done;
      });
      held = { entered, wait };
      return { started, release };
    },
  };
}
test("delivery review shows recipient and device counts, explicit refresh and Indonesian narrow layout", async ({
  page,
}) => {
  const api = await install(page);
  api.set({
    ...deliveryDetail(),
    email: {
      pending: 2,
      processing: 1,
      accepted: 2,
      failed: 1,
      superseded: 1,
      unavailableRecipients: 1,
    },
  });
  await page.goto(path);
  await page.getByRole("link", { name: "Notification delivery", exact: true }).click();
  const recipients = page.getByRole("region", { name: "Push recipients", exact: true });
  await expect(recipients).toBeVisible();
  await expect(recipients).toContainText("Failed1");
  await expect(page.getByRole("region", { name: "Device processing", exact: true })).toContainText(
    "Accepted by push service4",
  );
  await expect(
    page.getByText("Service acceptance does not confirm receipt or display on a device.", {
      exact: false,
    }),
  ).toBeVisible();
  await expect(page.getByRole("status").filter({ hasText: "seven days" })).toBeVisible();
  await expect(page.getByRole("region", { name: "Email reminders", exact: true })).toContainText(
    "Accepted by mail server2",
  );
  await expect(
    page.getByText(
      "Mail server acceptance does not confirm delivery to a mailbox or that the message was read.",
      { exact: true },
    ),
  ).toBeVisible();
  expect(api.requests).toHaveLength(1);
  api.set({
    ...deliveryDetail(),
    failed: 0,
    completed: 3,
    email: {
      pending: 2,
      processing: 1,
      accepted: 3,
      failed: 0,
      superseded: 1,
      unavailableRecipients: 1,
    },
  });
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(recipients).toContainText("Completed3");
  expect(api.requests).toHaveLength(2);
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(
    page.getByRole("heading", { name: "Pengiriman notifikasi", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("region", { name: "Penerima push", exact: true })).toContainText(
    "Selesai3",
  );
  await expect(page.getByRole("region", { name: "Pengingat email", exact: true })).toContainText(
    "Diterima server email3",
  );
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  expect(api.requests).toHaveLength(2);
  await page.screenshot({
    path: "../../.work/dashboard-push-delivery-id.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("link", { name: "Kembali ke pengumuman", exact: true }).click();
  await expect(page).toHaveURL(
    new RegExp(`/announcements/${announcementId}\\?company=${companyIds[0]}`),
  );
  expect(api.identity.unhandled).toEqual([]);
});
test("company replacement cannot publish a delayed observation from the previous company", async ({
  page,
}) => {
  const api = await install(page),
    held = api.hold();
  await page.goto(`${path}/delivery?company=${companyIds[0]}`);
  await held.started;
  await page
    .getByRole("combobox", { name: "Company", exact: true })
    .selectOption(companyIds[1] as string);
  await expect(page).toHaveURL(
    new RegExp(`/communications/announcements\\?company=${companyIds[1]}`),
  );
  held.release();
  await expect(page.getByRole("heading", { name: "Announcements", exact: true })).toBeVisible();
  await expect(page.getByRole("region", { name: "Push recipients", exact: true })).toHaveCount(0);
  expect(api.requests).toHaveLength(1);
  expect(api.identity.unhandled).toEqual([]);
});
test("backgrounding clears the observation and foreground recovery reads once", async ({
  page,
}) => {
  const api = await install(page);
  await page.goto(`${path}/delivery`);
  await expect(page.getByRole("region", { name: "Push recipients", exact: true })).toBeVisible();
  await page.evaluate(() => {
    Object.defineProperty(document, "visibilityState", { value: "hidden", configurable: true });
    document.dispatchEvent(new Event("visibilitychange"));
  });
  await expect(page.getByRole("region", { name: "Push recipients", exact: true })).toHaveCount(0);
  expect(api.requests).toHaveLength(1);
  await page.evaluate(() => {
    Object.defineProperty(document, "visibilityState", { value: "visible", configurable: true });
    document.dispatchEvent(new Event("visibilitychange"));
  });
  await expect(page.getByRole("region", { name: "Push recipients", exact: true })).toBeVisible();
  expect(api.requests).toHaveLength(2);
  api.identity.setPermissions([]);
  api.fail("access_denied");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("region", { name: "Push recipients", exact: true })).toHaveCount(0);
  await expect(page.getByText("Accepted by push service", { exact: true })).toHaveCount(0);
  expect(api.requests).toHaveLength(3);
  expect(api.identity.unhandled).toEqual([]);
});
