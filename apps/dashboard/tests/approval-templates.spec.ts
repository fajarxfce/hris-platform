import { expect, type Page, test } from "@playwright/test";
import { approverId, installApprovalTemplateApi, templateId } from "./approval-template-api";
import { companyIds } from "./identity-api";

const parameters = `company=${companyIds[0]}&kind=EXPENSE&asOf=2026-10-01`;
const catalog = `/approvals/templates?${parameters}`;
const detail = `/approvals/templates/${templateId()}?${parameters}`;
const editor = `/approvals/templates/${templateId()}/edit?${parameters}`;
const stage = (page: Page, index: number) =>
  page.getByRole("group", { name: `Stage ${index}`, exact: true });
async function openEditor(page: Page) {
  await page.goto(editor);
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("North approval 1");
  await page.getByLabel("Reason", { exact: true }).fill("Annual approval review");
}

test("template catalog retains its bounded cursor and selected rules revision", async ({
  page,
}) => {
  const api = await installApprovalTemplateApi(page);
  await page.goto(catalog);
  const table = page.getByRole("table", { name: "Approval templates", exact: true });
  await expect(table.getByRole("row")).toHaveCount(21);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(3);
  await page.getByRole("button", { name: "View: North approval 21", exact: true }).click();
  await expect(page.getByRole("region", { name: "Template details", exact: true })).toContainText(
    "1234567890123456.78",
  );
  await page.getByRole("link", { name: "Previous revision", exact: true }).click();
  await expect(page.getByRole("region", { name: "Template details", exact: true })).toContainText(
    "2026-02-01",
  );
  await expect(
    page.getByRole("region", { name: "Template details", exact: true }),
  ).not.toContainText("1234567890123456.78");
  await page.getByRole("link", { name: "Back to templates", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(3);
  await page.getByLabel("Effective on", { exact: true }).fill("2026-01-15");
  await page.getByRole("button", { name: "Apply filters", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(21);
  await expect(table).toContainText("2026-01-01");
  expect(api.identity.unhandled).toEqual([]);
});
test("editor loads the current version even when opened from an old revision", async ({ page }) => {
  const api = await installApprovalTemplateApi(page);
  await page.goto(`${detail}&revision=0`);
  await expect(page.getByRole("region", { name: "Template details", exact: true })).toContainText(
    "2026-01-01",
  );
  await page.getByRole("link", { name: "Edit current template", exact: true }).click();
  await expect(page.getByLabel("Minimum amount", { exact: true })).toHaveValue(
    "1234567890123456.78",
  );
  await expect(page.getByLabel("Type", { exact: true })).toHaveAttribute("readonly", "");
  await page.getByLabel("Reason", { exact: true }).fill("Threshold review");
  await page.getByRole("button", { name: "Save template", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Template saved.");
  expect(api.writes[0]?.body).toMatchObject({
    expectedVersion: 2,
    minimumAmount: "1234567890123456.78",
    kind: "EXPENSE",
  });
});
test("named stages use paginated literal-name lookup and keep assignments when reordered", async ({
  page,
}) => {
  const api = await installApprovalTemplateApi(page);
  await openEditor(page);
  await stage(page, 1).getByRole("button", { name: "Add approver", exact: true }).click();
  const picker = page.getByRole("dialog", { name: "Select approver", exact: true });
  await expect(picker.getByRole("table").getByRole("row")).toHaveCount(10);
  await picker.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(picker.getByRole("table").getByRole("row")).toHaveCount(3);
  await picker.getByLabel("Search by name", { exact: true }).fill("Scope_100%");
  await picker.getByRole("button", { name: "Search", exact: true }).click();
  await expect(picker.getByRole("table").getByRole("row")).toHaveCount(2);
  expect(api.writes).toEqual([]);
  await picker.getByRole("button", { name: "Select: Scope_100%", exact: true }).click();
  await expect(picker).toHaveCount(0);
  await stage(page, 1).getByRole("button", { name: "Move down", exact: true }).click();
  await expect(stage(page, 2)).toContainText("Scope_100%");
  await stage(page, 1).getByRole("button", { name: "Remove stage", exact: true }).click();
  await expect(stage(page, 1)).toContainText("Scope_100%");
  await expect(
    stage(page, 1).getByRole("button", { name: "Remove stage", exact: true }),
  ).toBeDisabled();
  await page.getByRole("button", { name: "Save template", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Template saved.");
  expect(api.writes[0]?.body.stages).toEqual([
    { assignment: "NAMED", accountIds: [approverId(), approverId(0, 12)], permission: null },
  ]);
  expect(
    api.reads
      .filter((url) => url.pathname.endsWith("assignees"))
      .at(-1)
      ?.searchParams.get("after"),
  ).toBeNull();
});
test("new template type changes reset incompatible assignments and validate before writing", async ({
  page,
}) => {
  const api = await installApprovalTemplateApi(page);
  await page.goto(`/approvals/templates/new?${parameters}`);
  await page.getByLabel("Name", { exact: true }).fill("Payroll review");
  await page.getByRole("button", { name: "Add stage", exact: true }).click();
  await page.getByRole("combobox", { name: "Type", exact: true }).selectOption("PAYROLL");
  await expect(stage(page, 2)).toHaveCount(0);
  await expect(stage(page, 1).getByLabel("Assignment", { exact: true })).toHaveValue("PERMISSION");
  await expect(stage(page, 1).getByLabel("Required permission", { exact: true })).toHaveValue(
    "payroll.review",
  );
  await page.getByLabel("Minimum amount", { exact: true }).fill("1e4");
  await page.getByLabel("Reason", { exact: true }).fill("Introduce independent review");
  await page.getByRole("button", { name: "Save template", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Check the template");
  expect(api.writes).toEqual([]);
  await page.getByLabel("Minimum amount", { exact: true }).fill("10000.00");
  await page.getByRole("button", { name: "Save template", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Template saved.");
  expect(api.writes[0]?.body).toMatchObject({
    expectedVersion: null,
    kind: "PAYROLL",
    minimumAmount: "10000.00",
    stages: [{ assignment: "PERMISSION", accountIds: [], permission: "payroll.review" }],
  });
  expect(api.reads).toEqual([]);
});
test("uncertain template saves recover the same receipt across MFA and a conflict response", async ({
  page,
}) => {
  const api = await installApprovalTemplateApi(page);
  await openEditor(page);
  api.loseNext();
  await page.getByRole("button", { name: "Save template", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save result is unconfirmed");
  await expect(page.getByRole("button", { name: "Add stage", exact: true })).toBeDisabled();
  api.identity.expireMfa();
  api.rejectNext("mfa_required", 403);
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  api.rejectNext("stale_version");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save result is unconfirmed");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Template saved.");
  expect(api.writes).toHaveLength(4);
  expect(api.commits).toBe(1);
  for (const write of api.writes) {
    expect(write.operation).toBe(api.writes[0]?.operation);
    expect(write.body).toEqual(api.writes[0]?.body);
  }
});
test("a definite conflict requires deliberate refresh and retains the edited fields until then", async ({
  page,
}) => {
  const api = await installApprovalTemplateApi(page);
  await openEditor(page);
  await page.getByLabel("Name", { exact: true }).fill("My proposed approval");
  api.advance();
  await page.getByRole("button", { name: "Save template", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("changed");
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("My proposed approval");
  await expect(page.getByRole("button", { name: "Save template", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Reload current version", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("Concurrent approval");
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed current rules");
  await page.getByRole("button", { name: "Save template", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Template saved.");
  expect(api.writes[1]?.body.expectedVersion).toBe(3);
  expect(api.writes[1]?.operation).not.toBe(api.writes[0]?.operation);
});
for (const kind of ["template", "templates"] as const)
  test(`a pending ${kind} read cannot cross the company boundary`, async ({ page }) => {
    const api = await installApprovalTemplateApi(page);
    const pending = api.hold(kind);
    try {
      await page.goto(kind === "template" ? detail : catalog);
      await pending.entered;
      await page
        .getByRole("combobox", { name: "Company", exact: true })
        .selectOption(companyIds[1]);
      await expect(page).toHaveURL(
        `/approvals/templates?company=${companyIds[1]}&kind=EXPENSE&asOf=2026-10-01`,
      );
      pending.release();
      await expect(page.getByRole("table")).toContainText("South approval 1");
      await expect(page.getByRole("main")).not.toContainText("North approval");
    } finally {
      pending.release();
    }
  });
test("closing the approver picker discards late data and leaves the form intact", async ({
  page,
}) => {
  const api = await installApprovalTemplateApi(page);
  await openEditor(page);
  const pending = api.hold("assignees");
  try {
    await stage(page, 1).getByRole("button", { name: "Add approver", exact: true }).click();
    await pending.entered;
    await page.getByRole("dialog").getByRole("button", { name: "Close", exact: true }).click();
    pending.release();
    await expect(page.getByRole("dialog")).toHaveCount(0);
    await expect(
      stage(page, 1).getByRole("button", { name: "Add approver", exact: true }),
    ).toBeFocused();
    await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Annual approval review");
    await stage(page, 1).getByRole("button", { name: "Add approver", exact: true }).click();
    await expect(page.getByRole("dialog").getByRole("table")).toBeVisible();
    await page.getByRole("dialog").getByRole("button", { name: "Close", exact: true }).click();
  } finally {
    pending.release();
  }
});
test("administration access is required before catalog, editor and detail I/O", async ({
  page,
}) => {
  const api = await installApprovalTemplateApi(page, ["approvals.read"]);
  for (const path of [catalog, detail, editor, `/approvals/templates/new?${parameters}`]) {
    await page.goto(path);
    await expect(page.getByRole("alert")).toContainText("You do not have access");
    await expect(page.getByRole("link", { name: "Approval templates", exact: true })).toHaveCount(
      0,
    );
  }
  expect(api.reads).toEqual([]);
  expect(api.writes).toEqual([]);
});
test("the staged editor supports mobile, dark mode and Indonesian without discarding changes", async ({
  page,
}) => {
  const api = await installApprovalTemplateApi(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await openEditor(page);
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue("Annual approval review");
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
    .toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-approval-template-mobile.png",
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Simpan template", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Template tersimpan.");
  expect(api.commits).toBe(1);
});

test("a confirmed history jump to another new-template context discards the former uncertain command", async ({
  page,
}) => {
  const api = await installApprovalTemplateApi(page);
  await page.goto(catalog);
  await page.getByRole("link", { name: "Create approval template", exact: true }).click();
  await page.getByRole("link", { name: "Back to templates", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Approval templates", level: 1, exact: true }),
  ).toBeVisible();
  await page.getByRole("combobox", { name: "Type", exact: true }).selectOption("LEAVE");
  await page.getByRole("button", { name: "Apply filters", exact: true }).click();
  await expect(page).toHaveURL(
    `/approvals/templates?company=${companyIds[0]}&kind=LEAVE&asOf=2026-10-01`,
  );
  await page.getByRole("link", { name: "Create approval template", exact: true }).click();
  await expect(page.getByRole("combobox", { name: "Type", exact: true })).toHaveValue("LEAVE");
  await page.getByLabel("Name", { exact: true }).fill("Pending leave policy");
  await page.getByLabel("Reason", { exact: true }).fill("Introduce approval stages");
  api.loseNext();
  await page.getByRole("button", { name: "Save template", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save result is unconfirmed");
  await page.evaluate(() => window.history.go(-3));
  const departure = page.getByRole("dialog", { name: "Leave this page?", exact: true });
  await expect(departure).toContainText("Changes may already be saved");
  await departure.getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page).toHaveURL(`/approvals/templates/new?${parameters}`);
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("");
  await expect(page.getByRole("button", { name: "Save template", exact: true })).toBeEnabled();
  await expect(page.getByRole("button", { name: "Retry save", exact: true })).toHaveCount(0);
  await expect(page.getByRole("combobox", { name: "Type", exact: true })).toHaveValue("EXPENSE");
  expect(api.writes).toHaveLength(1);
});
