import { expect, test } from "@playwright/test";
import { installAnnouncementEditorApi } from "./announcement-editor-api";
import { announcementId } from "./announcements-api";
import { companyIds } from "./identity-api";

const directory = "/communications/announcements";

test("selected labels can be retried without clearing the draft", async ({ page }) => {
  await installAnnouncementEditorApi(page);
  let failSelected = true;
  await page.route("**/api/v1/companies/*/communications/audience-references?*", async (route) => {
    if (failSelected && new URL(route.request().url()).searchParams.has("ids")) {
      failSelected = false;
      return route.fulfill({ status: 503, json: { code: "database_busy" } });
    }
    return route.fallback();
  });
  await page.goto(`${directory}/new`);
  await page.getByLabel("Title", { exact: true }).fill("Retained draft");
  await page.getByRole("combobox", { name: "Audience", exact: true }).selectOption("BRANCH");
  await page.getByRole("button", { name: "Add: South office", exact: true }).click();
  await page.getByRole("button", { name: "Reload selected references", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Remove: South office", exact: true }),
  ).toBeVisible();
  await expect(page.getByLabel("Title", { exact: true })).toHaveValue("Retained draft");
  await expect(page.getByRole("status")).toContainText("Selected: 1");
});

test("draft creation uses bounded recipient lookup and retains its form through locale and layout changes", async ({
  page,
}) => {
  const api = await installAnnouncementEditorApi(page);
  await page.goto(directory);
  await page.getByRole("link", { name: "New announcement", exact: true }).click();
  await page.getByLabel("Title", { exact: true }).fill("New office hours");
  await page
    .getByLabel("Message", { exact: true })
    .fill("The office opens at 08:00.\nContact your manager.");
  await page.getByLabel("Reason", { exact: true }).fill("Working hours review");
  await page.getByRole("combobox", { name: "Audience", exact: true }).selectOption("BRANCH");
  const available = page.getByRole("table", { name: "Available references", exact: true });
  await expect(available.getByRole("row")).toHaveCount(51);
  await page.getByRole("button", { name: "Add: South office", exact: true }).click();
  await page.getByLabel("Name or code", { exact: true }).fill("Office 51");
  await page.getByLabel("Name or code", { exact: true }).press("Enter");
  await page.getByRole("button", { name: "Add: Office 51", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Selected recipients", exact: true }).getByRole("row"),
  ).toHaveCount(3);
  expect(api.writes).toHaveLength(0);
  await page.getByRole("checkbox", { name: "Require acknowledgement", exact: true }).check();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByLabel("Judul", { exact: true })).toHaveValue("New office hours");
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: "../../.work/dashboard-announcement-editor.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Simpan draft", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Draft tersimpan.");
  expect(api.writes).toHaveLength(1);
  expect(api.writes[0]?.body).toMatchObject({
    audience: {
      kind: "BRANCH",
      targetIds: ["51000000-0000-4000-8000-000000000001", "51000000-0000-4000-8000-000000000051"],
    },
    acknowledgementRequired: true,
    expectedVersion: null,
  });
  await page.getByRole("link", { name: "Lihat pengumuman", exact: true }).click();
  await expect(page.getByRole("region", { name: "Pesan", exact: true })).toContainText(
    "The office opens at 08:00.",
  );
  expect(api.lookups.every((url) => url.searchParams.get("limit") === "50")).toBe(true);
  expect(api.identity.unhandled).toEqual([]);
});

test("an uncertain save retains the original command through verification and a later conflict", async ({
  page,
}) => {
  const api = await installAnnouncementEditorApi(page);
  await page.goto(`${directory}/${announcementId}/edit`);
  await page.getByLabel("Title", { exact: true }).fill("Retained office notice");
  await page.getByLabel("Reason", { exact: true }).fill("Hours review");
  api.dropNext();
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save result could not be confirmed");
  await expect(page.getByLabel("Title", { exact: true })).toHaveAttribute("readonly", "");
  api.identity.expireMfa();
  api.rejectNext("mfa_required");
  await page.getByRole("button", { name: "Check save result", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verify = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verify.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verify.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verify).toHaveCount(0);
  await expect(page.getByLabel("Title", { exact: true })).toHaveValue("Retained office notice");
  expect(api.writes).toHaveLength(2);
  api.rejectNext("stale_version");
  await page.getByRole("button", { name: "Check save result", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Review latest version", exact: true }),
  ).toBeDisabled();
  await page.getByRole("button", { name: "Check save result", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Draft saved.");
  expect(api.writes).toHaveLength(4);
  expect(api.commits).toBe(1);
  for (const write of api.writes) expect(write).toEqual(api.writes[0]);
  expect(api.identity.unhandled).toEqual([]);
});

test("a definite conflict requires protected re-review and a newly observed version", async ({
  page,
}) => {
  const api = await installAnnouncementEditorApi(page);
  await page.goto(`${directory}/${announcementId}/edit`);
  await page.getByLabel("Title", { exact: true }).fill("Local edit");
  await page.getByLabel("Reason", { exact: true }).fill("Office update");
  api.revise();
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(page.getByRole("button", { name: "Save draft", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Review latest version", exact: true }).click();
  const departure = page.getByRole("dialog", { name: "Leave this page?", exact: true });
  await departure.getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Title", { exact: true })).toHaveValue("Reviewed office hours");
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed current revision");
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Draft saved.");
  expect(api.writes.map((write) => write.body.expectedVersion)).toEqual([0, 1]);
  expect(api.writes[0]?.operation).not.toBe(api.writes[1]?.operation);
});

test("company replacement disposes a pending reference search and clears the draft", async ({
  page,
}) => {
  const api = await installAnnouncementEditorApi(page);
  await page.goto(`${directory}/new`);
  const pending = api.holdNextLookup();
  await page.getByRole("combobox", { name: "Audience", exact: true }).selectOption("BRANCH");
  await pending.start;
  try {
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
    await expect(page).toHaveURL(new RegExp(`company=${companyIds[1]}`));
  } finally {
    pending.release();
  }
  await expect(page.getByRole("heading", { name: "Announcements", exact: true })).toBeVisible();
  await expect(page.getByText("South office", { exact: true })).toHaveCount(0);
  expect(api.writes).toHaveLength(0);
});

test("a denied editor does not acquire announcement or reference data", async ({ page }) => {
  const api = await installAnnouncementEditorApi(page, false);
  await page.goto(`${directory}/new`);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.reads).toHaveLength(0);
  expect(api.lookups).toHaveLength(0);
  expect(api.writes).toHaveLength(0);
});
