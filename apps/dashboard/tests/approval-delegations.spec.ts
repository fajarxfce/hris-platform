import { expect, type Page, test } from "@playwright/test";
import {
  delegateId,
  delegationActor,
  delegationId,
  installApprovalDelegationApi,
} from "./approval-delegation-api";
import { companyIds } from "./identity-api";

const query = `company=${companyIds[0]}`;
const list = `/approvals/delegations?${query}`;
const detail = `/approvals/delegations/${delegationId()}?${query}`;
const editor = `/approvals/delegations/${delegationId()}/edit?${query}`;
async function openEditor(page: Page) {
  await page.goto(editor);
  await expect(page.getByLabel("Delegate", { exact: true })).toHaveValue(delegateId());
  await page.getByLabel("Reason", { exact: true }).fill("Review holiday coverage");
}

test("delegations show bounded incoming and outgoing pages and retained expired details", async ({
  page,
}) => {
  const api = await installApprovalDelegationApi(page);
  await page.goto(list);
  const table = page.getByRole("table", { name: "My delegations", exact: true });
  await expect(table.getByRole("row")).toHaveCount(21);
  await expect(table).toContainText("Disabled");
  await expect(table).toContainText("You");
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(3);
  await table.getByRole("button", { name: new RegExp(`${delegationId(0, 21)}$`, "u") }).click();
  await expect(page.getByRole("region", { name: "Delegation details", exact: true })).toContainText(
    delegationId(0, 21),
  );
  await page.getByRole("link", { name: "Back to delegations", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(3);
  await page.goto(`/approvals/delegations/${delegationId(0, 23)}?${query}`);
  await expect(page.getByRole("region", { name: "Delegation details", exact: true })).toContainText(
    "2020",
  );
  expect(api.identity.unhandled).toEqual([]);
});
test("creating a delegation uses bounded approver lookup, company time and a single versioned command", async ({
  page,
}) => {
  const api = await installApprovalDelegationApi(page);
  await page.goto(list);
  await page.getByRole("link", { name: "Create delegation", exact: true }).click();
  await page.getByLabel("Starts at", { exact: true }).fill("2030-10-01T09:00");
  await page.getByLabel("Ends at", { exact: true }).fill("2030-10-05T17:00");
  await page.getByLabel("Reason", { exact: true }).fill("Annual leave coverage");
  await page.getByRole("button", { name: "Select delegate", exact: true }).click();
  const picker = page.getByRole("dialog", { name: "Select approver", exact: true });
  await expect(
    picker.getByRole("button", { name: "Select: Sample Reviewer", exact: true }),
  ).toHaveCount(0);
  await picker.getByRole("button", { name: "Next page", exact: true }).click();
  await picker.getByLabel("Search by name", { exact: true }).fill("Delegate_100%");
  await picker.getByRole("button", { name: "Search", exact: true }).click();
  await expect(picker.getByRole("table").getByRole("row")).toHaveCount(2);
  expect(api.writes).toEqual([]);
  await picker.getByRole("button", { name: "Select: Delegate_100%", exact: true }).click();
  await expect(page.getByLabel("Delegate", { exact: true })).toHaveValue("Delegate_100%");
  await page.getByRole("button", { name: "Save delegation", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Delegation saved.");
  expect(api.writes[0]?.body).toEqual({
    kind: "LEAVE",
    fromAccount: delegationActor,
    toAccount: delegateId(0, 12),
    validFrom: "2030-10-01T02:00:00Z",
    validUntil: "2030-10-05T10:00:00Z",
    active: true,
    expectedVersion: null,
    reason: "Annual leave coverage",
  });
  await page.getByRole("link", { name: "View delegation", exact: true }).click();
  await expect(page.getByRole("region", { name: "Delegation details", exact: true })).toContainText(
    delegateId(0, 12),
  );
});
test("editing preserves the original delegator and stored microsecond timestamps", async ({
  page,
}) => {
  const api = await installApprovalDelegationApi(page);
  await openEditor(page);
  await expect(page.getByRole("button", { name: "Select delegator", exact: true })).toHaveCount(0);
  await page.getByRole("checkbox", { name: "Enabled", exact: true }).uncheck();
  await page.getByRole("button", { name: "Save delegation", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Delegation saved.");
  expect(api.writes[0]?.body).toMatchObject({
    expectedVersion: 2,
    fromAccount: delegationActor,
    active: false,
    validFrom: "2030-10-01T00:00:00.123456Z",
    validUntil: "2030-10-10T00:00:00.123456Z",
  });
});
test("recipients can read a delegation without editing it or selecting another delegator", async ({
  page,
}) => {
  const api = await installApprovalDelegationApi(page, {
    permissions: ["approvals.read", "leave.approve"],
  });
  await page.goto(`/approvals/delegations/${delegationId(0, 2)}?${query}`);
  await expect(page.getByRole("region", { name: "Delegation details", exact: true })).toContainText(
    delegationActor,
  );
  await expect(page.getByRole("link", { name: "Edit delegation", exact: true })).toHaveCount(0);
  await page.goto(`/approvals/delegations/${delegationId(0, 2)}/edit?${query}`);
  await expect(page.getByRole("alert")).toContainText("access");
  await expect(page.getByRole("button", { name: "Save delegation", exact: true })).toHaveCount(0);
  await page.goto(`/approvals/delegations/new?${query}`);
  await expect(page.getByLabel("Delegator", { exact: true })).toHaveValue(delegationActor);
  await expect(page.getByRole("button", { name: "Select delegator", exact: true })).toHaveCount(0);
  expect(api.writes).toEqual([]);
});
test("new admin delegation may select a different delegator while excluding the delegate", async ({
  page,
}) => {
  const api = await installApprovalDelegationApi(page);
  await page.goto(`/approvals/delegations/new?${query}`);
  const picker = page.getByRole("dialog", { name: "Select approver", exact: true });
  await page.getByRole("button", { name: "Select delegate", exact: true }).click();
  await picker.getByRole("button", { name: "Select: Delegate 01", exact: true }).click();
  await page.getByRole("button", { name: "Select delegator", exact: true }).click();
  await expect(
    picker.getByRole("button", { name: "Select: Delegate 01", exact: true }),
  ).toHaveCount(0);
  await picker.getByRole("button", { name: "Select: Delegate 02", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Manager coverage arrangement");
  await page.getByRole("button", { name: "Save delegation", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Delegation saved.");
  expect(api.writes[0]?.body).toMatchObject({
    fromAccount: delegateId(0, 2),
    toAccount: delegateId(),
  });
});
test("changing the approval type clears the delegate and old picker work cannot restore it", async ({
  page,
}) => {
  const api = await installApprovalDelegationApi(page);
  await openEditor(page);
  const held = api.holdNext("assignees");
  await page.getByRole("button", { name: "Select delegate", exact: true }).click();
  await held.entered;
  await page.getByRole("dialog").getByRole("button", { name: "Close", exact: true }).click();
  await expect(page.getByRole("button", { name: "Select delegate", exact: true })).toBeFocused();
  await page.getByRole("combobox", { name: "Type", exact: true }).selectOption("EXPENSE");
  held.release();
  await expect(page.getByLabel("Delegate", { exact: true })).toHaveValue("");
  await page.getByRole("button", { name: "Save delegation", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Check both accounts");
  expect(api.writes).toEqual([]);
  await page.getByRole("button", { name: "Select delegate", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Select: Delegate 02", exact: true })
    .click();
  await page.getByRole("button", { name: "Save delegation", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Delegation saved.");
  expect(api.writes[0]?.body.kind).toBe("EXPENSE");
  expect(
    api.reads
      .filter((url) => url.pathname.endsWith("assignees"))
      .at(-1)
      ?.searchParams.get("kind"),
  ).toBe("EXPENSE");
});
test("unconfirmed saves retain one operation through MFA and later conflicts", async ({ page }) => {
  const api = await installApprovalDelegationApi(page);
  await openEditor(page);
  api.loseNext();
  await page.getByRole("button", { name: "Save delegation", exact: true }).click();
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
  api.rejectNext("stale_version");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save result is unconfirmed");
  await expect(page.getByRole("button", { name: "Select delegate", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Delegation saved.");
  expect(api.commits).toBe(1);
  expect(api.writes).toHaveLength(4);
  for (const item of api.writes) {
    expect(item.operation).toBe(api.writes[0]?.operation);
    expect(item.body).toEqual(api.writes[0]?.body);
  }
});
test("a confirmed version conflict requires protected reload before another save", async ({
  page,
}) => {
  const api = await installApprovalDelegationApi(page);
  await openEditor(page);
  api.advance();
  await page.getByRole("button", { name: "Save delegation", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("changed");
  await expect(page.getByRole("button", { name: "Save delegation", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Reload current version", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByRole("checkbox", { name: "Enabled", exact: true })).not.toBeChecked();
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed current coverage");
  await page.getByRole("button", { name: "Save delegation", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Delegation saved.");
  expect(api.writes[1]?.body.expectedVersion).toBe(3);
  expect(api.writes[1]?.operation).not.toBe(api.writes[0]?.operation);
});
test("company replacement drops a pending detail and its old cursor", async ({ page }) => {
  const api = await installApprovalDelegationApi(page);
  const held = api.holdNext("detail");
  await page.goto(`${detail}&after=${delegationId(0, 20)}`);
  await held.entered;
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  await expect(
    page.getByRole("table", { name: "My delegations", exact: true }).getByRole("row"),
  ).toHaveCount(2);
  held.release();
  await expect(page).toHaveURL(
    new RegExp(`/approvals/delegations\\?company=${companyIds[1]}$`, "u"),
  );
  await expect(page.getByRole("main")).not.toContainText(delegationId());
  expect(
    api.reads
      .filter((url) => url.pathname.includes(companyIds[1]))
      .every((url) => !url.searchParams.has("after")),
  ).toBe(true);
});
test("permission denial prevents reads, writes and navigation to delegation administration", async ({
  page,
}) => {
  const api = await installApprovalDelegationApi(page, { permissions: ["approvals.manage"] });
  for (const path of [list, detail, editor, `/approvals/delegations/new?${query}`]) {
    await page.goto(path);
    await expect(page.getByRole("alert")).toContainText("access");
    await expect(
      page.getByRole("navigation").getByRole("link", { name: "My delegations", exact: true }),
    ).toHaveCount(0);
  }
  expect(api.reads).toEqual([]);
  expect(api.writes).toEqual([]);
});
test("ambiguous company time and excessive periods fail before a command is sent", async ({
  page,
}) => {
  const api = await installApprovalDelegationApi(page, { timezone: "America/New_York" });
  await openEditor(page);
  await page.getByLabel("Starts at", { exact: true }).fill("2030-11-03T01:30");
  await page.getByLabel("Ends at", { exact: true }).fill("2030-11-05T12:00");
  await page.getByRole("button", { name: "Save delegation", exact: true }).click();
  await expect(
    page.getByText("Enter a valid time. During a clock change, choose a time that occurs once.", {
      exact: true,
    }),
  ).toBeVisible();
  expect(api.writes).toEqual([]);
  await page.getByLabel("Starts at", { exact: true }).fill("2030-01-01T12:00");
  await page.getByRole("button", { name: "Save delegation", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("up to 90 days");
  expect(api.writes).toEqual([]);
});
test("Indonesian dark delegation editor remains usable on a narrow screen", async ({ page }) => {
  await installApprovalDelegationApi(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await openEditor(page);
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Edit delegasi", exact: true })).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Pilih penerima delegasi", exact: true }),
  ).toHaveCSS("color", "rgb(255, 255, 255)");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({
    path: "../../.work/dashboard-approval-delegation-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Simpan delegasi", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Delegasi tersimpan.");
});
