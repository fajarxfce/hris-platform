import { expect, type Page, test } from "@playwright/test";
import { companyIds } from "./identity-api";
import { departmentId, installOrganizationEditorApi, officeId } from "./organization-editor-api";

async function createForm(page: Page) {
  await page.goto("/organization/units");
  await page.getByRole("link", { name: "Create unit", exact: true }).click();
  await page.getByLabel("Code", { exact: true }).fill(" rnd ");
  await page.getByLabel("Name", { exact: true }).fill("Research & development");
}
const departure = (page: Page) =>
  page.getByRole("dialog", { name: "Leave this page?", exact: true });

test("organization editing is responsive and localized, uses a bounded parent picker and saves normalized values", async ({
  page,
}) => {
  const api = await installOrganizationEditorApi(page, { manyParents: true });
  await createForm(page);
  await page.screenshot({
    path: "../../.work/dashboard-organization-editor-light.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "Choose parent unit", exact: true }).click();
  const picker = page.getByRole("dialog", { name: "Choose parent unit", exact: true });
  await expect(picker.getByRole("table").getByRole("row")).toHaveCount(51);
  await picker.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(picker.getByRole("table")).toContainText("Parent 54");
  expect(api.reads.at(-1)?.searchParams.get("after")).not.toBeNull();
  const reads = api.reads.length;
  await picker.getByLabel("Name or code", { exact: true }).fill("North office");
  expect(api.reads).toHaveLength(reads);
  await picker.getByRole("button", { name: "Apply", exact: true }).click();
  await picker.getByRole("button", { name: "Select: North office (HQ)", exact: true }).click();
  await expect(picker).toHaveCount(0);
  await expect(page.getByRole("region", { name: "Parent unit", exact: true })).toContainText(
    "North office (HQ)",
  );
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.screenshot({
    path: "../../.work/dashboard-organization-editor-dark.png",
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByLabel("Nama", { exact: true })).toBeVisible();
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
    .toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-organization-editor-mobile.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "Simpan", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Unit tersimpan.");
  expect(api.writes).toHaveLength(1);
  expect(api.writes[0]?.body).toEqual({
    code: "RND",
    name: "Research & development",
    kind: "DEPARTMENT",
    parentId: officeId,
    timezone: null,
    active: true,
    expectedVersion: null,
  });
  expect(api.writes[0]?.csrf).toMatch(/^fixture-csrf-/u);
  await page.getByRole("link", { name: "Lihat detail", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Research & development", exact: true }),
  ).toBeVisible();
});

test("route, Back, company and logout departures retain edits until an explicit choice", async ({
  page,
}) => {
  const api = await installOrganizationEditorApi(page);
  await createForm(page);
  const name = page.getByLabel("Name", { exact: true });
  const overview = page.getByRole("link", { name: "Overview", exact: true });
  await overview.click();
  await expect(departure(page)).toContainText("Unsaved changes");
  await departure(page).getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(overview).toBeFocused();
  await expect(name).toHaveValue("Research & development");
  await page.evaluate(() => window.history.back());
  await expect(departure(page)).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(name).toHaveValue("Research & development");
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  await departure(page).getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(page.getByRole("combobox", { name: "Company", exact: true })).toHaveValue(
    companyIds[0],
  );
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await departure(page).getByRole("button", { name: "Stay on this page", exact: true }).click();
  expect(api.identity.commands).toHaveLength(0);
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Organization", exact: true })).toBeVisible();
  await expect(page.getByRole("table")).toContainText("South office");
  await expect(name).toHaveCount(0);
  expect(api.writes).toHaveLength(0);
});

test("unknown saves remain immutable through MFA and recover the same receipt only after explicit retry", async ({
  page,
}) => {
  const api = await installOrganizationEditorApi(page);
  await createForm(page);
  api.loseNextSave();
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save outcome is not confirmed");
  await expect(page.getByLabel("Name", { exact: true })).toHaveJSProperty("readOnly", true);
  const original = api.writes[0];
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
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("Research & development");
  await expect(page.getByRole("button", { name: "Save", exact: true })).toBeDisabled();
  expect(api.writes).toHaveLength(2);
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Unit saved.");
  expect(api.writes).toHaveLength(3);
  expect(api.commits).toBe(1);
  for (const write of api.writes) {
    expect(write.operation).toBe(original?.operation);
    expect(write.body).toEqual(original?.body);
    expect(write.id).toBe(original?.id);
  }
  api.failDetails("connection_unavailable");
  await page.getByRole("link", { name: "View details", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Unable to connect");
  await expect(page.getByRole("button", { name: "Retry save", exact: true })).toHaveCount(0);
  expect(api.writes).toHaveLength(3);
});

test("a stale revision preserves the draft until reload is confirmed and deactivation uses the new version", async ({
  page,
}) => {
  const api = await installOrganizationEditorApi(page);
  await page.goto(`/organization/units/${departmentId}`);
  await page.getByRole("link", { name: "Edit unit", exact: true }).click();
  await expect(page.getByRole("combobox", { name: "Type", exact: true })).toBeDisabled();
  await page.getByLabel("Name", { exact: true }).fill("My revision");
  api.replaceDepartment("Concurrent revision", 8);
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("This record has changed");
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("My revision");
  await page.getByRole("button", { name: "Reload latest", exact: true }).click();
  await departure(page).getByRole("button", { name: "Stay on this page", exact: true }).click();
  await page.getByRole("button", { name: "Reload latest", exact: true }).click();
  await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("Concurrent revision");
  await page.getByRole("combobox", { name: "Status", exact: true }).selectOption("false");
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Unit saved.");
  expect(api.writes.map((write) => write.body.expectedVersion)).toEqual([7, 8]);
  expect(api.writes[1]?.body.active).toBe(false);
  expect(api.writes[1]?.operation).not.toBe(api.writes[0]?.operation);
});

test("leaving a pending save warns about its outcome and a late response cannot restore the old company", async ({
  page,
}) => {
  const api = await installOrganizationEditorApi(page);
  await createForm(page);
  const waiting = api.holdSave();
  try {
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await waiting.started;
    await expect(page.getByRole("button", { name: "Save", exact: true })).toBeDisabled();
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(departure(page)).toContainText("Changes may already be saved");
    await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
    await expect(page.getByRole("table")).toContainText("South office");
    waiting.release();
    await expect(page.getByRole("main")).not.toContainText("Research & development");
    expect(api.writes).toHaveLength(1);
  } finally {
    waiting.release();
  }
});

test("revocation clears an open editor and its pending navigation without reusing the old intent", async ({
  page,
}) => {
  const api = await installOrganizationEditorApi(page);
  await createForm(page);
  await page.getByRole("link", { name: "Overview", exact: true }).click();
  await expect(departure(page)).toBeVisible();
  api.identity.setPermissions(["company.read"]);
  await page.evaluate(() => window.dispatchEvent(new Event("online")));
  await expect(departure(page)).toHaveCount(0);
  await expect(page.getByRole("alert")).toContainText("You do not have access");
  await expect(page.getByLabel("Name", { exact: true })).toHaveCount(0);
  await expect(page).toHaveURL(/\/organization\/units\/new\?/u);
  expect(api.writes).toHaveLength(0);
});

test("direct editor links without management never load or submit a unit", async ({ page }) => {
  const api = await installOrganizationEditorApi(page, { permissions: ["company.read"] });
  await page.goto(`/organization/units/${departmentId}/edit`);
  await expect(page.getByRole("alert")).toContainText("You do not have access");
  expect(api.reads).toHaveLength(0);
  expect(api.writes).toHaveLength(0);
});

test("browser reload uses a native unsaved-changes prompt and confirmed logout clears the editor", async ({
  page,
}) => {
  const api = await installOrganizationEditorApi(page);
  await createForm(page);
  const prompt = page.waitForEvent("dialog");
  const reload = page.evaluate(() => window.location.reload());
  const dialog = await prompt;
  expect(dialog.type()).toBe("beforeunload");
  await dialog.dismiss();
  await reload;
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("Research & development");
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Sign in", exact: true })).toBeVisible();
  await expect
    .poll(() => api.identity.commands.filter((command) => command.path.endsWith("/logout")).length)
    .toBe(1);
});

for (const code of ["session_revoked", "company_access_denied"]) {
  test(`a command reporting ${code} revalidates the workspace and removes the former draft`, async ({
    page,
  }) => {
    const api = await installOrganizationEditorApi(page);
    await createForm(page);
    api.loseNextSave();
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("The save outcome is not confirmed");
    if (code === "session_revoked") api.identity.revoke();
    else api.identity.denyAccess(code);
    api.rejectNextSave(code);
    await page.getByRole("button", { name: "Retry save", exact: true }).click();
    await expect(page.getByLabel("Name", { exact: true })).toHaveCount(0);
    if (code === "company_access_denied") {
      await expect(page.getByRole("alert")).toContainText("Company access is unavailable");
      await page.getByRole("button", { name: "Sign out", exact: true }).click();
    }
    await expect(page.getByRole("heading", { name: "Sign in", exact: true })).toBeVisible();
    expect(api.writes).toHaveLength(2);
    expect(api.writes[1]?.operation).toBe(api.writes[0]?.operation);
    expect(api.commits).toBe(1);
  });
}

test("keyboard skip focus preserves router history and Back still protects an edited form", async ({
  page,
}) => {
  await installOrganizationEditorApi(page);
  const warnings: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "warning") warnings.push(message.text());
  });
  await page.goto("/organization/units");
  await page.getByRole("link", { name: "Create unit", exact: true }).click();
  await page.getByLabel("Name", { exact: true }).fill("Unsaved unit");
  const currentUrl = page.url();
  await page.getByRole("button", { name: "Skip to content", exact: true }).focus();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("main")).toBeFocused();
  await expect(page).toHaveURL(currentUrl);
  await page.goBack();
  await departure(page).getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("Unsaved unit");
  expect(warnings.some((message) => message.includes("use a blocker on a POP navigation"))).toBe(
    false,
  );
});
