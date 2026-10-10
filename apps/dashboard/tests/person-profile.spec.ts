import { expect, type Page, test } from "@playwright/test";
import { companyIds } from "./identity-api";
import {
  installPersonProfileApi,
  profileEmployeeIds,
  profilePersonIds,
} from "./person-profile-api";

const profilePath = `/people/employees/${profileEmployeeIds[0]}/profile`;
const departure = (page: Page) =>
  page.getByRole("dialog", { name: "Leave this page?", exact: true });
async function edit(page: Page) {
  await page.goto(`${profilePath}/edit`);
  await expect(page.getByLabel("Legal name", { exact: true })).toHaveValue("Alya Pratama");
  await page.getByLabel("Legal name", { exact: true }).fill("Alya Revised");
  await page.getByLabel("Reason for change", { exact: true }).fill("Verified correction");
}

test("private profile and history load only on their routes with bounded localized responsive views", async ({
  page,
}) => {
  const api = await installPersonProfileApi(page, { longHistory: true });
  await page.goto(`/people/employees/${profileEmployeeIds[0]}?asOf=2026-10-01`);
  await expect(page.getByRole("heading", { name: "Alya Pratama", exact: true })).toBeVisible();
  expect(api.reads.some((url) => url.pathname.includes("/profile"))).toBe(false);
  await expect(page.getByText("Birth date", { exact: true })).toHaveCount(0);
  await page.getByRole("link", { name: "Personal profile", exact: true }).click();
  const overview = page.getByRole("region", { name: "Personal data", exact: true });
  await expect(overview).toContainText("Jun 7, 1995");
  expect(api.reads.some((url) => url.pathname.endsWith("/profile/history"))).toBe(false);
  await page.screenshot({
    path: "../../.work/dashboard-person-profile-light.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("tab", { name: "Profile history", exact: true }).click();
  const history = page.getByRole("table", { name: "Profile history", exact: true });
  await expect(history.getByRole("row")).toHaveCount(51);
  await history.getByRole("button", { name: "View revision: 0", exact: true }).click();
  const panel = page.getByRole("dialog", { name: "Profile revision details", exact: true });
  await expect(panel).toContainText("Initial profile");
  await expect(panel).toContainText("Previous name 0");
  await page.keyboard.press("Escape");
  await expect(
    history.getByRole("button", { name: "View revision: 0", exact: true }),
  ).toBeFocused();
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(page).toHaveURL(/profileAfter=49/u);
  await expect(history.getByRole("row")).toHaveCount(6);
  await page.goBack();
  await expect(history.getByRole("row")).toHaveCount(51);
  expect(
    api.reads
      .filter((url) => url.pathname.endsWith("/history"))
      .every((url) => url.searchParams.get("limit") === "50"),
  ).toBe(true);
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.getByRole("tab", { name: "Ringkasan", exact: true }).click();
  await expect(page.getByRole("region", { name: "Data pribadi", exact: true })).toContainText(
    "Indonesia (ID)",
  );
  await page.getByRole("link", { name: "Edit profil", exact: true }).click();
  await expect(page.getByLabel("Nama lengkap", { exact: true })).toHaveValue("Alya Pratama");
  await page.screenshot({
    path: "../../.work/dashboard-person-profile-editor-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByLabel("Alasan perubahan", { exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: "../../.work/dashboard-person-profile-editor-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(api.writes).toHaveLength(0);
});

test("profile edits translate field errors, preserve the draft, and leave employment versions unchanged", async ({
  page,
}) => {
  const api = await installPersonProfileApi(page);
  await edit(page);
  api.rejectNextSave("invalid_person", { nationality: "invalid_country" });
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(
    page.getByText("Enter a valid two-letter country code.", { exact: true }),
  ).toBeVisible();
  await expect(page.getByLabel("Legal name", { exact: true })).toHaveValue("Alya Revised");
  await expect(page.getByText("PRIVATE TECHNICAL ERROR", { exact: true })).toHaveCount(0);
  await page.getByLabel("Nationality", { exact: true }).fill("sg");
  await page.getByLabel("Birth date", { exact: true }).fill("");
  await page.getByLabel("Email", { exact: true }).fill("");
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Profile saved.");
  expect(api.writes).toHaveLength(2);
  expect(api.writes[1]?.body).toEqual({
    expectedVersion: 7,
    legalName: "Alya Revised",
    birthDate: null,
    nationality: "SG",
    email: null,
    reason: "Verified correction",
  });
  expect(api.writes[1]?.operation).not.toBe(api.writes[0]?.operation);
  await page.getByRole("link", { name: "View profile", exact: true }).click();
  const data = page.getByRole("region", { name: "Personal data", exact: true });
  await expect(data).toContainText("Singapore (SG)");
  await expect(
    data.locator(".app-property-row").filter({ hasText: "Profile version" }),
  ).toContainText("8");
  await expect(data).toContainText(profilePersonIds[0]);
  await page.getByRole("link", { name: "Back", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Alya Revised", exact: true })).toBeVisible();
  await expect(
    page
      .getByRole("region", { name: "Employment", exact: true })
      .locator(".app-property-row")
      .filter({ hasText: "Employment version" }),
  ).toContainText("3");
});

test("an uncertain profile save survives MFA and recovers one receipt without an automatic command", async ({
  page,
}) => {
  const api = await installPersonProfileApi(page);
  await edit(page);
  api.loseNextSave();
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save outcome is not confirmed");
  await expect(page.getByLabel("Legal name", { exact: true })).toHaveAttribute("readonly", "");
  api.identity.expireMfa();
  api.rejectNextSave("mfa_required");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  await expect(page.getByLabel("Reason for change", { exact: true })).toHaveValue(
    "Verified correction",
  );
  expect(api.writes).toHaveLength(2);
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Profile saved.");
  expect(api.writes).toHaveLength(3);
  expect(api.commits).toBe(1);
  expect(
    api.writes.every(
      (write) =>
        write.operation === api.writes[0]?.operation &&
        JSON.stringify(write.body) === JSON.stringify(api.writes[0]?.body),
    ),
  ).toBe(true);
  expect(api.writes[2]?.csrf).not.toBe(api.writes[0]?.csrf);
  api.failReads("profile", "connection_unavailable");
  await page.getByRole("link", { name: "View profile", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Unable to connect");
  expect(api.writes).toHaveLength(3);
});

test("conflicts keep profile edits until a protected reload explicitly accepts the current version", async ({
  page,
}) => {
  const api = await installPersonProfileApi(page);
  await edit(page);
  api.replaceProfile({ legalName: "Updated elsewhere", version: 9 });
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("This record has changed");
  await page.getByRole("button", { name: "Reload latest profile", exact: true }).click();
  await departure(page).getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(page.getByLabel("Legal name", { exact: true })).toHaveValue("Alya Revised");
  await page.getByRole("button", { name: "Reload latest profile", exact: true }).click();
  await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Legal name", { exact: true })).toHaveValue("Updated elsewhere");
  await page.getByLabel("Reason for change", { exact: true }).fill("Confirm correction");
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Profile saved.");
  expect(api.writes.map((write) => write.body.expectedVersion)).toEqual([7, 9]);
  expect(api.commits).toBe(1);
});

for (const kind of ["profile", "history"] as const) {
  test(`company switching discards pending ${kind} data and the former employee URL`, async ({
    page,
  }) => {
    const api = await installPersonProfileApi(page);
    await page.goto(profilePath);
    await expect(page.getByRole("heading", { name: "Alya Pratama", exact: true })).toBeVisible();
    if (kind === "history") {
      await page.getByRole("tab", { name: "Profile history", exact: true }).click();
      await expect(page.getByRole("table")).toBeVisible();
    }
    const held = api.holdNext(kind);
    try {
      await page.getByRole("button", { name: "Refresh", exact: true }).click();
      await held.entered;
      await page
        .getByRole("combobox", { name: "Company", exact: true })
        .selectOption(companyIds[1]);
      await expect(page.getByRole("heading", { name: "Employees", exact: true })).toBeVisible();
      held.release();
      await expect(page.getByRole("table")).toContainText("Bayu Selatan");
      await expect(page.getByText("Alya Pratama", { exact: true })).toHaveCount(0);
      await expect(page).not.toHaveURL(new RegExp(profileEmployeeIds[0], "u"));
    } finally {
      held.release();
    }
  });
}

test("private profile reads require their own grant before I/O", async ({ page }) => {
  const api = await installPersonProfileApi(page, { permissions: ["people.read"] });
  await page.goto(profilePath);
  await expect(page.getByRole("alert")).toContainText("You do not have access");
  expect(api.reads).toHaveLength(0);
  expect(api.writes).toHaveLength(0);
});

test("a profile read grant does not authorize a direct editor link", async ({ page }) => {
  const api = await installPersonProfileApi(page, { permissions: ["people.profile.read"] });
  await page.goto(profilePath);
  await expect(page.getByRole("region", { name: "Personal data", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Edit profile", exact: true })).toHaveCount(0);
  const count = api.reads.length;
  await page.goto(`${profilePath}/edit`);
  await expect(page.getByRole("alert")).toContainText("You do not have access");
  expect(api.reads).toHaveLength(count);
  expect(api.writes).toHaveLength(0);
});

test("a management grant cannot edit a profile owned by another company", async ({ page }) => {
  const api = await installPersonProfileApi(page, {
    permissions: ["people.profile.read", "people.profile.manage"],
  });
  api.replaceProfile({ ownerCompanyId: companyIds[1] });
  await page.goto(profilePath);
  await expect(
    page.getByText("This profile is managed by its owning company.", { exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("link", { name: "Edit profile", exact: true })).toHaveCount(0);
  await page.goto(`${profilePath}/edit`);
  await expect(page.getByRole("alert")).toContainText("Edit this profile from its owning company");
  await expect(page.getByLabel("Legal name", { exact: true })).toHaveCount(0);
  expect(api.writes).toHaveLength(0);
});

test("dirty profiles require a company departure choice and revoked saves remove all private fields", async ({
  page,
}) => {
  const api = await installPersonProfileApi(page);
  await edit(page);
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  await departure(page).getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(page.getByLabel("Legal name", { exact: true })).toHaveValue("Alya Revised");
  api.loseNextSave();
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save outcome is not confirmed");
  api.identity.revoke();
  api.rejectNextSave("session_revoked");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Sign in", exact: true })).toBeVisible();
  await expect(page.getByLabel("Legal name", { exact: true })).toHaveCount(0);
  await expect(departure(page)).toHaveCount(0);
  expect(api.writes).toHaveLength(2);
  expect(api.commits).toBe(1);
});

test("losing sensitive history access clears both the history and the private overview", async ({
  page,
}) => {
  const api = await installPersonProfileApi(page);
  await page.goto(profilePath);
  await expect(page.getByRole("heading", { name: "Alya Pratama", exact: true })).toBeVisible();
  await page.getByRole("tab", { name: "Profile history", exact: true }).click();
  await expect(page.getByRole("table")).toBeVisible();
  api.failReads("history", "access_denied");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("You do not have access");
  await expect(page.getByText("Alya Pratama", { exact: true })).toHaveCount(0);
  await expect(page.getByRole("table")).toHaveCount(0);
  await expect(page.getByRole("region", { name: "Personal data", exact: true })).toHaveCount(0);
});

test("self profile access does not acquire sensitive history or expose editing", async ({
  page,
}) => {
  const api = await installPersonProfileApi(page, { permissions: ["people.self.read"] });
  await page.goto(`${profilePath}?company=${companyIds[0]}&tab=history&profileAfter=0`);
  await expect(page.getByRole("region", { name: "Personal data", exact: true })).toBeVisible();
  await expect(page.getByRole("tab", { name: "Profile history", exact: true })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Edit profile", exact: true })).toHaveCount(0);
  expect(api.reads.every((url) => url.pathname.endsWith("/profile"))).toBe(true);
  expect(api.writes).toHaveLength(0);
});

test("an invalid profile history cursor fails locally and recovers only through first-page navigation", async ({
  page,
}) => {
  const api = await installPersonProfileApi(page);
  await page.goto(`${profilePath}?company=${companyIds[0]}&tab=history&profileAfter=01`);
  await expect(page.getByRole("alert")).toContainText("This page link is invalid");
  expect(api.reads.some((url) => url.pathname.endsWith("/profile/history"))).toBe(false);
  await page.getByRole("button", { name: "First page", exact: true }).click();
  await expect(page.getByRole("table", { name: "Profile history", exact: true })).toBeVisible();
  await expect(page).not.toHaveURL(/profileAfter=/u);
});
