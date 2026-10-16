import { expect, type Page, test } from "@playwright/test";
import { installCompanyMembershipEditorApi } from "./company-membership-editor-api";
import { companyMembershipId } from "./fixtures/company-memberships";
import { roleTemplateId } from "./fixtures/role-templates";
import { companyIds } from "./identity-api";

async function openEditor(page: Page, path: string) {
  await page.goto(path);
  await expect(page.getByRole("heading", { name: "Example Colleague", exact: true })).toBeVisible();
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed company responsibilities");
}
async function replaceRole(page: Page) {
  await page.getByRole("button", { name: "Replace role", exact: true }).click();
  await page
    .getByRole("button", {
      name: "Use this version: Operations reviewer 1 (REVIEWER_1)",
      exact: true,
    })
    .click();
}

test("member editing keeps applied role versions while direct permissions change", async ({
  page,
}) => {
  const api = await installCompanyMembershipEditorApi(page);
  await page.goto(`/administration/members/${companyMembershipId}`);
  await page.getByRole("link", { name: "Edit company access", exact: true }).click();
  await expect(page.getByText("Previously applied version: 0", { exact: true })).toBeVisible();
  expect(api.roles.reads.filter((url) => !url.pathname.endsWith("permissions"))).toHaveLength(0);
  await page
    .getByRole("region", { name: "Direct permissions", exact: true })
    .locator("summary")
    .filter({ hasText: "People" })
    .click();
  await page.getByRole("checkbox", { name: "View employees", exact: true }).check();
  await page.getByLabel("Reason", { exact: true }).fill("Additional reviewed responsibilities");
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-membership-editor.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Save access", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Company access saved.");
  expect(api.writes[0]?.body).toMatchObject({
    expectedVersion: 4,
    permissions: ["company.read", "people.read"],
    roleTemplates: [{ id: roleTemplateId(), version: 0, retainApplied: true }],
  });
  expect(api.grants.get(companyIds[0])?.roleTemplates[0]?.permissions).toEqual([
    "people.team.read",
  ]);
  expect(api.roles.identity.unhandled).toEqual([]);
});

test("role replacement explicitly selects the reviewed current version", async ({ page }) => {
  const api = await installCompanyMembershipEditorApi(page);
  await openEditor(page, api.editor);
  await replaceRole(page);
  await expect(page.getByText("Selected version: 21", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Save access", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Company access saved.");
  expect(api.writes[0]?.body.roleTemplates).toEqual([
    { id: roleTemplateId(), version: 21, retainApplied: false },
  ]);
});

test("role picker pages remain bounded and closing restores the editor", async ({ page }) => {
  const api = await installCompanyMembershipEditorApi(page, { longPage: true });
  await openEditor(page, api.editor);
  await page.getByRole("button", { name: "Add role", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "Choose role", exact: true });
  const table = dialog.getByRole("table", { name: "Role versions", exact: true });
  await expect(table.getByRole("row")).toHaveCount(21);
  await expect(
    table.getByRole("button", {
      name: "Use this version: Operations reviewer 1 (REVIEWER_1)",
      exact: true,
    }),
  ).toBeDisabled();
  await dialog.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(2);
  await table
    .getByRole("button", {
      name: "Use this version: Operations reviewer 21 (REVIEWER_21)",
      exact: true,
    })
    .click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue(
    "Reviewed company responsibilities",
  );
  await page.getByRole("button", { name: "Save access", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Company access saved.");
  expect(api.writes[0]?.body.roleTemplates).toHaveLength(2);
  expect(
    api.roles.reads
      .filter((url) => !url.pathname.endsWith("permissions"))
      .every((url) => url.searchParams.get("limit") === "20"),
  ).toBe(true);
});

test("reactivation requires current role review while inactive accounts can be disabled", async ({
  page,
}) => {
  const api = await installCompanyMembershipEditorApi(page);
  const grant = api.grants.get(companyIds[0]);
  if (!grant) throw new Error("Missing member fixture");
  grant.member.active = false;
  await openEditor(page, api.editor);
  await page.getByRole("checkbox", { name: "Company access active", exact: true }).check();
  await expect(page.getByRole("button", { name: "Save access", exact: true })).toBeDisabled();
  await expect(page.getByRole("alert")).toContainText("Select active role versions");
  await replaceRole(page);
  await page.getByRole("button", { name: "Save access", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Company access saved.");
  const changed = api.grants.get(companyIds[0]);
  if (!changed) throw new Error("Missing saved fixture");
  changed.member.accountActive = false;
  await openEditor(page, api.editor);
  await expect(page.getByRole("button", { name: "Save access", exact: true })).toBeDisabled();
  await page.getByRole("checkbox", { name: "Company access active", exact: true }).uncheck();
  await page.getByRole("button", { name: "Save access", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Company access saved.");
  expect(api.writes[1]?.body.active).toBe(false);
});

test("unconfirmed membership saves preserve the original command through MFA and later conflicts", async ({
  page,
}) => {
  const api = await installCompanyMembershipEditorApi(page);
  await openEditor(page, api.editor);
  api.dropNext();
  await page.getByRole("button", { name: "Save access", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save outcome is unconfirmed.");
  const reads = api.reads.length;
  api.roles.identity.expireMfa();
  api.rejectNext("mfa_required");
  await page.getByRole("button", { name: "Check save outcome", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await expect(verification).toBeVisible();
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  api.rejectNext("stale_version");
  await page.getByRole("button", { name: "Check save outcome", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Review current version", exact: true }),
  ).toHaveCount(0);
  await page.getByRole("button", { name: "Check save outcome", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Company access saved.");
  expect(api.writes).toHaveLength(4);
  for (const write of api.writes) expect(write).toEqual(api.writes[0]);
  expect(api.reads).toHaveLength(reads);
});

test("definite membership conflicts require protected current review before another command", async ({
  page,
}) => {
  const api = await installCompanyMembershipEditorApi(page);
  await openEditor(page, api.editor);
  api.advance();
  await page.getByRole("button", { name: "Save access", exact: true }).click();
  await page.getByRole("button", { name: "Review current version", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed newer membership");
  await page.getByRole("button", { name: "Save access", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Company access saved.");
  expect(api.writes.map((write) => write.body.expectedVersion)).toEqual([4, 5]);
  expect(api.writes[0]?.operation).not.toBe(api.writes[1]?.operation);
});

test("pending membership saves disable edits and guard departure", async ({ page }) => {
  const api = await installCompanyMembershipEditorApi(page);
  await openEditor(page, api.editor);
  const held = api.holdSave();
  await page.getByRole("button", { name: "Save access", exact: true }).click();
  await held.entered;
  await expect(page.getByText("Saving access…", { exact: true })).toBeVisible();
  await expect(
    page.getByRole("checkbox", { name: "Company access active", exact: true }),
  ).toBeDisabled();
  await page.getByRole("link", { name: "Back to account access", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Stay on this page", exact: true })
    .click();
  held.release();
  await expect(page.getByRole("status")).toContainText("Company access saved.");
  expect(api.writes).toHaveLength(1);
});

test("company replacement cancels pending editor reads and clears private grant data", async ({
  page,
}) => {
  const api = await installCompanyMembershipEditorApi(page);
  await page.goto(api.editor);
  await expect(page.getByRole("heading", { name: "Example Colleague", exact: true })).toBeVisible();
  const held = api.holdRead();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await held.entered;
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  await expect(page.getByRole("table", { name: "Account directory", exact: true })).toContainText(
    "Other colleague",
  );
  held.release();
  await expect(page.getByText("colleague@example.invalid", { exact: true })).toHaveCount(0);
  await expect(page).toHaveURL(new RegExp(`company=${companyIds[1]}`, "u"));
});

test("denied company administrators acquire neither member grants nor role catalogs", async ({
  page,
}) => {
  const api = await installCompanyMembershipEditorApi(page, { allowed: false });
  await page.goto(api.editor);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.reads).toHaveLength(0);
  expect(api.roles.reads).toHaveLength(0);
});

test("membership editing remains readable in Indonesian dark mode with doubled text", async ({
  page,
}) => {
  const api = await installCompanyMembershipEditorApi(page);
  await openEditor(page, api.editor);
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  const original = await page
    .getByLabel("Alasan", { exact: true })
    .evaluate((el) => Number.parseFloat(getComputedStyle(el).fontSize));
  await page.evaluate(() => {
    const sizes = [...document.querySelectorAll("main, main *")]
      .filter((el): el is HTMLElement => el instanceof HTMLElement)
      .map((el) => ({
        el,
        size: Number.parseFloat(getComputedStyle(el).fontSize),
        line: Number.parseFloat(getComputedStyle(el).lineHeight),
      }));
    for (const { el, size, line } of sizes) {
      el.style.fontSize = `${size * 2}px`;
      if (Number.isFinite(line)) el.style.lineHeight = `${line * 2}px`;
    }
    window.scrollTo(0, 0);
  });
  expect(
    await page
      .getByLabel("Alasan", { exact: true })
      .evaluate((el) => Number.parseFloat(getComputedStyle(el).fontSize)),
  ).toBe(original * 2);
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue(
    "Reviewed company responsibilities",
  );
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: "../../.work/dashboard-membership-editor-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
});
