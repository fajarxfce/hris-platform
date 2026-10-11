import { expect, test } from "@playwright/test";
import { announcementId } from "../src/features/communications/di/communications-fixture";
import { installAnnouncementPublicationApi } from "./announcement-publication-api";
import { companyIds } from "./identity-api";

const path = `/communications/announcements/${announcementId}`;
test.use({ timezoneId: "America/New_York" });
test("audience preview is explicit and publication uses the company time zone after confirmation", async ({
  page,
}) => {
  const api = await installAnnouncementPublicationApi(page);
  await page.goto(`${path}/publication`);
  await expect(page.getByRole("heading", { name: "Office closure", exact: true })).toBeVisible();
  expect(api.previews).toHaveLength(0);
  await page.getByRole("button", { name: "Preview audience", exact: true }).click();
  await expect(page.getByRole("region", { name: "Eligible audience", exact: true })).toContainText(
    "3",
  );
  await page.getByRole("link", { name: "Publish announcement", exact: true }).click();
  await page.getByRole("checkbox", { name: "Schedule publication", exact: true }).check();
  await page.getByLabel("Date and time · Asia/Jakarta", { exact: true }).fill("2026-11-01T08:00");
  await page.getByLabel("Reason", { exact: true }).fill("Scheduled office notice");
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "Publish announcement", exact: true });
  await expect(dialog).toBeVisible();
  expect(api.writes).toHaveLength(0);
  await dialog.getByRole("button", { name: "Cancel", exact: true }).click();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue("Scheduled office notice");
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: "../../.work/dashboard-announcement-publication.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Lanjutkan", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Konfirmasi", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Publikasi masuk antrean.");
  expect(api.writes).toHaveLength(1);
  expect(api.writes[0]?.body).toEqual({
    expectedVersion: 0,
    reason: "Scheduled office notice",
    scheduledFor: "2026-11-01T01:00:00Z",
  });
  await page.getByRole("link", { name: "Lihat publikasi", exact: true }).click();
  await expect(page.getByRole("region", { name: "Job publikasi", exact: true })).toContainText(
    "Antrean",
  );
  await expect(page.getByRole("link", { name: "Job publikasi", exact: true })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Arsipkan pengumuman", exact: true })).toHaveCount(0);
  expect(api.identity.unhandled).toEqual([]);
});

test("an uncertain publication survives verification and later rejection without changing the command", async ({
  page,
}) => {
  const api = await installAnnouncementPublicationApi(page);
  await page.goto(`${path}/publish`);
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed announcement");
  api.dropNext();
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Confirm", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The command result could not be confirmed");
  await expect(page.getByLabel("Reason", { exact: true })).toHaveAttribute("readonly", "");
  api.identity.expireMfa();
  api.rejectNext("mfa_required");
  await page.getByRole("button", { name: "Check command result", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verify = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verify.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verify.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verify).toHaveCount(0);
  expect(api.writes).toHaveLength(2);
  api.rejectNext("stale_version");
  await page.getByRole("button", { name: "Check command result", exact: true }).click();
  await expect(page.getByRole("button", { name: "Review latest", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Check command result", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Publication queued.");
  expect(api.writes).toHaveLength(4);
  expect(api.commits).toBe(1);
  for (const write of api.writes) expect(write).toEqual(api.writes[0]);
  expect(api.identity.unhandled).toEqual([]);
});

test("stopped publication recovery and archival each require an observed version and confirmation", async ({
  page,
}) => {
  const api = await installAnnouncementPublicationApi(page);
  api.stopPublication();
  await page.goto(`${path}/publication`);
  await expect(page.getByRole("region", { name: "Publication job", exact: true })).toContainText(
    "Failed",
  );
  await page.getByRole("link", { name: "Return to draft", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Review delivery audience");
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  expect(api.writes).toHaveLength(0);
  await page.getByRole("dialog").getByRole("button", { name: "Confirm", exact: true }).click();
  await page.getByRole("link", { name: "View publication", exact: true }).click();
  await expect(page.getByRole("link", { name: "Publish announcement", exact: true })).toBeVisible();
  await page.getByRole("link", { name: "Archive announcement", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Superseded notice");
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Confirm", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Announcement archived.");
  expect(
    api.writes.map((write) => [write.path.split("/").at(-1), write.body.expectedVersion]),
  ).toEqual([
    ["return-to-draft", 1],
    ["archive", 2],
  ]);
  await page.getByRole("link", { name: "View publication", exact: true }).click();
  await expect(page.getByRole("link", { name: "Publish announcement", exact: true })).toHaveCount(
    0,
  );
  expect(api.previews).toHaveLength(0);
  expect(api.identity.unhandled).toEqual([]);
});

test("a definite conflict needs a fresh preview before a new publication command", async ({
  page,
}) => {
  const api = await installAnnouncementPublicationApi(page);
  await page.goto(`${path}/publish`);
  await page.getByLabel("Reason", { exact: true }).fill("Original review");
  api.revise();
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Confirm", exact: true }).click();
  await expect(page.getByRole("button", { name: "Continue", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Review latest", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Leave this page?", exact: true })
    .getByRole("button", { name: "Leave page", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Reviewed office notice", exact: true }),
  ).toBeVisible();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed updated content");
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Confirm", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Publication queued.");
  expect(api.previews.map((url) => url.searchParams.get("expectedVersion"))).toContain("1");
  expect(api.writes.map((write) => write.body.expectedVersion)).toEqual([0, 1]);
  expect(api.writes[0]?.operation).not.toBe(api.writes[1]?.operation);
});

test("company replacement discards a pending preview without sending a command", async ({
  page,
}) => {
  const api = await installAnnouncementPublicationApi(page);
  const pending = api.holdPreviews();
  await page.goto(`${path}/publish`);
  await pending.start;
  try {
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page).toHaveURL(new RegExp(`company=${companyIds[1]}`));
  } finally {
    pending.release();
  }
  await expect(page.getByRole("heading", { name: "Announcements", exact: true })).toBeVisible();
  await expect(
    page.getByText("The office will be closed on Friday.", { exact: false }),
  ).toHaveCount(0);
  expect(api.writes).toHaveLength(0);
});

test("denied review and publication acquire no private announcement data", async ({ page }) => {
  const api = await installAnnouncementPublicationApi(page, false);
  await page.goto(`${path}/publication`);
  await expect(page.getByRole("alert")).toBeVisible();
  await page.goto(`${path}/publish`);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.reads).toHaveLength(0);
  expect(api.writes).toHaveLength(0);
});
