import { expect, type Page, test } from "@playwright/test";
import { roleTemplateId } from "./fixtures/role-templates";
import { companyIds } from "./identity-api";
import { installRoleTemplatesApi } from "./role-templates-api";

async function openEditor(page: Page) {
  await page.goto(`/administration/roles/${roleTemplateId()}/edit`);
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("Operations reviewer 1");
  await page.getByLabel("Name", { exact: true }).fill("Reviewed team access");
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed team responsibilities");
}
async function openGroup(page: Page, group: string) {
  await page
    .getByRole("region", { name: "Permissions", exact: true })
    .locator("summary")
    .filter({ hasText: group })
    .click();
}
test("roles keep bounded directory and revision pages and offer readable localized layouts", async ({
  page,
}) => {
  const api = await installRoleTemplatesApi(page, { longPage: true });
  await page.goto("/administration/roles");
  const table = page.getByRole("table", { name: "Role directory", exact: true });
  await expect(table.getByRole("row")).toHaveCount(21);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(2);
  await page.goBack();
  await expect(table.getByRole("row")).toHaveCount(21);
  await table
    .getByRole("button", { name: "View role: Operations reviewer 1", exact: true })
    .click();
  const history = page.getByRole("table", { name: "Change history", exact: true });
  await expect(history.getByRole("row")).toHaveCount(21);
  await page.getByRole("button", { name: "View revision: Revision 21", exact: true }).click();
  await expect(page.getByRole("region", { name: "Revision 21", exact: true })).toBeFocused();
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-company-role.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Older revisions", exact: true }).click();
  await expect(history.getByRole("row")).toHaveCount(3);
  await page.getByRole("button", { name: "View revision: Revision 0", exact: true }).click();
  await expect(page.getByRole("region", { name: "Revision 0", exact: true })).toContainText(
    "Reviewed access revision 0",
  );
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByRole("region", { name: "Definisi saat ini", exact: true })).toContainText(
    "Operations reviewer 1",
  );
  expect(
    api.reads
      .filter((url) => !url.pathname.endsWith("permissions"))
      .every(
        (url) =>
          url.searchParams.get("limit") === "20" || url.searchParams.get("historyLimit") === "20",
      ),
  ).toBe(true);
  expect(api.identity.unhandled).toEqual([]);
});
test("creation selects explicit module permissions and keeps the server's normalized code", async ({
  page,
}) => {
  const api = await installRoleTemplatesApi(page);
  await page.goto("/administration/roles/new");
  await page.getByLabel("Code", { exact: true }).fill(" team_review ");
  await page.getByLabel("Name", { exact: true }).fill("Team reviewer");
  await openGroup(page, "People");
  await page.getByRole("checkbox", { name: "View team employees", exact: true }).check();
  await page.getByLabel("Reason", { exact: true }).fill("Team supervision");
  await page.getByRole("button", { name: "Save role", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Role saved.");
  expect(api.writes[0]?.body).toMatchObject({
    code: "TEAM_REVIEW",
    permissions: ["people.team.read"],
    expectedVersion: null,
  });
  await page.getByRole("link", { name: "View role", exact: true }).click();
  await expect(page.getByRole("region", { name: "Current definition", exact: true })).toContainText(
    "TEAM_REVIEW",
  );
});
test("responsive dark editor retains selected permissions at 200 percent text", async ({
  page,
}) => {
  await installRoleTemplatesApi(page);
  await openEditor(page);
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await page
    .getByRole("region", { name: "Permission", exact: true })
    .locator("summary")
    .filter({ hasText: "Karyawan" })
    .click();
  await expect(page.getByRole("checkbox", { name: "Lihat karyawan", exact: true })).toBeChecked();
  const originalSize = await page
    .getByLabel("Alasan", { exact: true })
    .evaluate((element) => Number.parseFloat(getComputedStyle(element).fontSize));
  await page.evaluate(() => {
    const sizes = [...document.querySelectorAll("main, main *")]
      .filter((element): element is HTMLElement => element instanceof HTMLElement)
      .map((element) => ({
        element,
        size: Number.parseFloat(getComputedStyle(element).fontSize),
        line: Number.parseFloat(getComputedStyle(element).lineHeight),
      }));
    for (const { element, size, line } of sizes) {
      element.style.fontSize = `${size * 2}px`;
      if (Number.isFinite(line)) element.style.lineHeight = `${line * 2}px`;
    }
    window.scrollTo(0, 0);
  });
  expect(
    await page
      .getByLabel("Alasan", { exact: true })
      .evaluate((element) => Number.parseFloat(getComputedStyle(element).fontSize)),
  ).toBe(originalSize * 2);
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: "../../.work/dashboard-company-role-editor-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
});
test("unconfirmed saves retain the original command through MFA and a later version conflict", async ({
  page,
}) => {
  const api = await installRoleTemplatesApi(page);
  await openEditor(page);
  api.dropNext();
  await page.getByRole("button", { name: "Save role", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save result is unconfirmed");
  const reads = api.reads.length;
  await expect(page.getByLabel("Name", { exact: true })).toHaveAttribute("readonly", "");
  api.identity.expireMfa();
  api.rejectNext("mfa_required");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const dialog = page.getByRole("dialog", { name: "Account verification", exact: true });
  await dialog.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await dialog.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue(
    "Reviewed team responsibilities",
  );
  api.rejectNext("stale_version");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("button", { name: "Reload and review", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Role saved.");
  expect(api.writes).toHaveLength(4);
  for (const write of api.writes) expect(write).toEqual(api.writes[0]);
  expect(api.reads).toHaveLength(reads);
});
test("confirmed version conflicts require a protected reload before a new command", async ({
  page,
}) => {
  const api = await installRoleTemplatesApi(page);
  await openEditor(page);
  api.advance(companyIds[0], roleTemplateId());
  await page.getByRole("button", { name: "Save role", exact: true }).click();
  await page.getByRole("button", { name: "Reload and review", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("Other writer role");
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed updated definition");
  await page.getByRole("button", { name: "Save role", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Role saved.");
  expect(api.writes.map((write) => write.body.expectedVersion)).toEqual([21, 22]);
  expect(api.writes[0]?.operation).not.toBe(api.writes[1]?.operation);
});
test("pending saves disable edits and protect navigation while their request is unresolved", async ({
  page,
}) => {
  const api = await installRoleTemplatesApi(page);
  await openEditor(page);
  const held = api.holdSave();
  await page.getByRole("button", { name: "Save role", exact: true }).click();
  await held.entered;
  await expect(page.getByRole("button", { name: "Save role", exact: true })).toBeDisabled();
  await expect(page.getByLabel("Name", { exact: true })).toHaveAttribute("readonly", "");
  await page.getByRole("link", { name: "Back to roles", exact: true }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Stay on this page", exact: true })
    .click();
  held.release();
  await expect(page.getByRole("status")).toContainText("Role saved.");
  expect(api.writes).toHaveLength(1);
});
test("company changes cancel a held directory read and discard old cursors", async ({ page }) => {
  const api = await installRoleTemplatesApi(page);
  await page.goto("/administration/roles");
  await expect(page.getByRole("table", { name: "Role directory", exact: true })).toContainText(
    "Operations reviewer 1",
  );
  const held = api.holdRead();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await held.entered;
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  await expect(page.getByRole("table", { name: "Role directory", exact: true })).toContainText(
    "Other company role",
  );
  held.release();
  await expect(page.getByText("Operations reviewer 1", { exact: true })).toHaveCount(0);
  await expect(page).toHaveURL(new RegExp(`company=${companyIds[1]}`, "u"));
});
test("denied role administration makes no role requests and hides navigation", async ({ page }) => {
  const api = await installRoleTemplatesApi(page, { allowed: false });
  await page.goto("/administration/roles/new");
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("link", { name: "Company roles", exact: true })).toHaveCount(0);
  expect(api.reads).toHaveLength(0);
  expect(api.writes).toHaveLength(0);
});
test("a failed refresh clears the current definition, permissions and selected history", async ({
  page,
}) => {
  const api = await installRoleTemplatesApi(page);
  await page.goto(`/administration/roles/${roleTemplateId()}`);
  await page.getByRole("button", { name: "View revision: Revision 21", exact: true }).click();
  api.failReads("role_template_not_found");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("This role is unavailable");
  await expect(page.getByRole("region", { name: "Current definition", exact: true })).toHaveCount(
    0,
  );
  await expect(page.getByRole("region", { name: "Revision 21", exact: true })).toHaveCount(0);
  await expect(page.getByText("PRIVATE ROLE DIAGNOSTIC", { exact: true })).toHaveCount(0);
});
