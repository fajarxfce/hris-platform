import { expect, test } from "@playwright/test";
import { announcementId, installAnnouncementsApi } from "./announcements-api";
import { companyIds } from "./identity-api";

const path = "/communications/announcements";
test("announcements keep bounded pages, immutable revision navigation and localized responsive content", async ({
  page,
}) => {
  const runtimeErrors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") runtimeErrors.push(message.text());
  });
  const api = await installAnnouncementsApi(page, { longPage: true });
  await page.goto(path);
  const table = page.getByRole("table", { name: "Announcements", exact: true });
  await expect(table.getByRole("row")).toHaveCount(51);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(2);
  await expect(table).toContainText("Notice 51");
  await page.goBack();
  await page.getByRole("button", { name: "View: Office closure", exact: true }).click();
  const content = page.getByRole("region", { name: "Message", exact: true });
  await expect(content).toContainText("The office will be closed on Friday.");
  await expect(content.locator("script")).toHaveCount(0);
  await page.screenshot({
    path: "../../.work/dashboard-announcement.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("link", { name: "Revision history", exact: true }).click();
  const history = page.getByRole("table", { name: "Revision history", exact: true });
  await expect(history.getByRole("row")).toHaveCount(4);
  await page.getByRole("button", { name: "View: Office closure · Version 0", exact: true }).click();
  await expect(content).toContainText("Original office notice.");
  await expect(page.getByRole("status")).toContainText("Historical revision");
  await page.getByRole("link", { name: "Current version", exact: true }).click();
  await expect(content).toContainText("The office will be closed on Friday.");
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByRole("region", { name: "Pesan", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  expect(
    api.reads
      .filter((url) => url.pathname.endsWith("announcements"))
      .every((url) => url.searchParams.get("limit") === "50"),
  ).toBe(true);
  expect(api.identity.unhandled).toEqual([]);
  expect(runtimeErrors).toEqual([]);
});

test("announcement management requires its own permission and clears unavailable content", async ({
  page,
}) => {
  const api = await installAnnouncementsApi(page);
  await page.goto(`${path}/${announcementId}`);
  await expect(page.getByRole("region", { name: "Message", exact: true })).toBeVisible();
  api.fail("announcement_not_found");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("This announcement is unavailable");
  await expect(page.getByRole("region", { name: "Message", exact: true })).toHaveCount(0);
  await expect(page.getByText("PRIVATE SERVER DETAIL", { exact: true })).toHaveCount(0);
});

test("denied announcement access does not issue a feature request", async ({ page }) => {
  const api = await installAnnouncementsApi(page, { allowed: false });
  await page.goto(path);
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("link", { name: "Announcements", exact: true })).toHaveCount(0);
  expect(api.reads).toHaveLength(0);
});

test("switching companies clears previous cursors and ignores a late announcement read", async ({
  page,
}) => {
  const api = await installAnnouncementsApi(page);
  await page.goto(path);
  await expect(page.getByRole("table", { name: "Announcements", exact: true })).toContainText(
    "Office closure",
  );
  const pending = api.holdNextRead();
  try {
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await pending.start;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("status")).toContainText("No announcements found.");
  } finally {
    pending.release();
  }
  await expect(page.getByText("Office closure", { exact: true })).toHaveCount(0);
  await expect(page).toHaveURL(new RegExp(`company=${companyIds[1]}`, "u"));
});
