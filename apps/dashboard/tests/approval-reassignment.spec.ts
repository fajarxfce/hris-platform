import { expect, type Page, test } from "@playwright/test";
import { installApprovalReassignmentApi, reassignmentAccount } from "./approval-reassignment-api";
import { approvalId } from "./approvals-api";
import { companyIds } from "./identity-api";

const query = `company=${companyIds[0]}`;
const detail = `/approvals/${approvalId(0, 2)}?${query}`;
const editor = `/approvals/${approvalId(0, 2)}/reassign?${query}`;
async function selectApprover(page: Page, name = "Approver 01") {
  await page.getByRole("button", { name: "Add approver", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Select approver", exact: true })
    .getByRole("button", { name: `Select: ${name}`, exact: true })
    .click();
}
async function openEditor(page: Page) {
  await page.goto(editor);
  await expect(
    page.getByRole("region", { name: "Request under review", exact: true }),
  ).toContainText("Blocked");
  await page.getByLabel("Reason", { exact: true }).fill("Original reviewer unavailable");
}
test("reassignment replaces only the reviewed stage and preserves its company and inbox context", async ({
  page,
}) => {
  const api = await installApprovalReassignmentApi(page);
  await page.goto(`${detail}&after=${approvalId(0, 1)}`);
  await page.getByRole("link", { name: "Reassign approvers", exact: true }).click();
  await selectApprover(page, "Approver 04");
  await page.getByLabel("Reason", { exact: true }).fill("Reviewer coverage");
  await page.getByRole("button", { name: "Confirm reassignment", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Approvers updated.");
  expect(api.writes[0]?.body).toEqual({
    version: 1,
    assignees: [reassignmentAccount(4)],
    reason: "Reviewer coverage",
  });
  expect(api.record.stages).toEqual([
    { assignees: [reassignmentAccount(2)] },
    { assignees: [reassignmentAccount(4)] },
    { assignees: [reassignmentAccount(3)] },
  ]);
  await page.getByRole("link", { name: "View request", exact: true }).click();
  await expect(page).toHaveURL(`${new URL(page.url()).origin}${detail}&after=${approvalId(0, 1)}`);
  await expect(page.getByRole("table", { name: "Approval stages", exact: true })).toContainText(
    reassignmentAccount(4),
  );
});
test("the picker excludes author, beneficiary and retained makers, and the form validates before writing", async ({
  page,
}) => {
  const api = await installApprovalReassignmentApi(page);
  await openEditor(page);
  await page.getByRole("button", { name: "Confirm reassignment", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Select 1–25");
  expect(api.writes).toEqual([]);
  await page.getByRole("button", { name: "Add approver", exact: true }).click();
  const picker = page.getByRole("dialog", { name: "Select approver", exact: true });
  await picker.getByLabel("Search by name", { exact: true }).fill("Maker");
  await picker.getByRole("button", { name: "Search", exact: true }).click();
  await expect(picker).toContainText("No eligible accounts found.");
  await picker.getByLabel("Search by name", { exact: true }).fill("Approver");
  await picker.getByRole("button", { name: "Search", exact: true }).click();
  await picker.getByRole("button", { name: "Next page", exact: true }).click();
  await picker.getByRole("button", { name: "Select: Approver 12", exact: true }).click();
  expect(api.writes).toEqual([]);
  await page.getByRole("button", { name: "Confirm reassignment", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Approvers updated.");
  expect(api.writes[0]?.body.assignees).toEqual([reassignmentAccount(12)]);
});
test("a lost response recovers its receipt without reading a newer request or changing the command", async ({
  page,
}) => {
  const api = await installApprovalReassignmentApi(page);
  await openEditor(page);
  await selectApprover(page);
  const readCount = api.reads.length;
  api.loseNext();
  await page.getByRole("button", { name: "Confirm reassignment", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save result is unconfirmed");
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
  api.rejectNext("approval_changed");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save result is unconfirmed");
  await expect(page.getByRole("button", { name: "Add approver", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Approvers updated.");
  expect(api.commits).toBe(1);
  expect(api.writes).toHaveLength(4);
  expect(api.reads).toHaveLength(readCount);
  for (const write of api.writes) {
    expect(write.operation).toBe(api.writes[0]?.operation);
    expect(write.body).toEqual(api.writes[0]?.body);
  }
});
test("an advanced stage requires deliberate review before any replacement command", async ({
  page,
}) => {
  const api = await installApprovalReassignmentApi(page);
  await openEditor(page);
  await selectApprover(page);
  api.advance();
  await page.getByRole("button", { name: "Confirm reassignment", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("The approval changed");
  await expect(
    page.getByRole("button", { name: "Confirm reassignment", exact: true }),
  ).toBeDisabled();
  await page.getByRole("button", { name: "Reload current version", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(
    page.getByRole("region", { name: "Request under review", exact: true }),
  ).toContainText("Pending");
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  await expect(
    page.getByRole("button", { name: "Remove approver: Approver 01", exact: true }),
  ).toHaveCount(0);
  await selectApprover(page, "Approver 05");
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed next stage");
  await page.getByRole("button", { name: "Confirm reassignment", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Approvers updated.");
  expect(api.writes[1]?.body.version).toBe(2);
  expect(api.writes[1]?.operation).not.toBe(api.writes[0]?.operation);
  expect(api.record.stages[2]?.assignees).toEqual([reassignmentAccount(5)]);
});
test("read-only users and completed requests have no reassignment action", async ({ page }) => {
  const api = await installApprovalReassignmentApi(page, ["approvals.read", "leave.approve"]);
  await page.goto(detail);
  await expect(page.getByRole("region", { name: "Request details", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Reassign approvers", exact: true })).toHaveCount(0);
  const before = api.reads.length;
  await page.goto(editor);
  await expect(page.getByRole("alert")).toContainText("access");
  expect(api.reads).toHaveLength(before);
  expect(api.writes).toEqual([]);
});
test("completed requests decline direct reassignment even for an administrator", async ({
  page,
}) => {
  const api = await installApprovalReassignmentApi(page);
  api.complete();
  await page.goto(detail);
  await expect(page.getByRole("region", { name: "Request details", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Reassign approvers", exact: true })).toHaveCount(0);
  await page.goto(editor);
  await expect(page.getByRole("alert")).toContainText("no longer pending");
  await expect(page.getByRole("button", { name: "Confirm reassignment", exact: true })).toHaveCount(
    0,
  );
  expect(api.writes).toEqual([]);
});
test("switching company discards a pending review without carrying the request or cursor across", async ({
  page,
}) => {
  const api = await installApprovalReassignmentApi(page);
  const held = api.hold("request");
  await page.goto(`${editor}&after=${approvalId(0, 1)}`);
  await held.entered;
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  await expect(
    page.getByRole("table", { name: "Approval inbox", exact: true }).getByRole("row"),
  ).toHaveCount(2);
  held.release();
  await expect(page).toHaveURL(new RegExp(`/approvals\\?company=${companyIds[1]}$`, "u"));
  await expect(
    page.getByRole("heading", { name: "Reassign current stage", exact: true }),
  ).toHaveCount(0);
  expect(api.writes).toEqual([]);
});
test("reassignment preserves its selection on cancelled departure and supports Indonesian dark mobile layout", async ({
  page,
}) => {
  await installApprovalReassignmentApi(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await openEditor(page);
  await selectApprover(page);
  await page.getByRole("link", { name: "Back to request", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Stay on this page", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Remove approver: Approver 01", exact: true }),
  ).toBeVisible();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByRole("button", { name: "Tambah approver", exact: true })).toHaveCSS(
    "color",
    "rgb(255, 255, 255)",
  );
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({
    path: "../../.work/dashboard-approval-reassignment-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Konfirmasi penggantian", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Approver diperbarui.");
});
