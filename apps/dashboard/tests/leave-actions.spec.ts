import { expect, type Page, test } from "@playwright/test";
import { leaveId } from "./fixtures/leave";
import { companyIds } from "./identity-api";
import { installLeaveActionsApi } from "./leave-actions-api";

const query = `company=${companyIds[0]}`;
const detail = `/leave/requests/${leaveId(0, 2)}?${query}`;
const action = (intent: string) => `/leave/requests/${leaveId(0, 2)}/${intent}?${query}`;
async function openAction(page: Page, intent = "approve") {
  await page.goto(action(intent));
  await expect(
    page.getByRole("region", { name: "Request under review", exact: true }),
  ).toContainText("North Employee 02");
}
for (const [intent, label, endpoint, status] of [
  ["approve", "Approve leave", "decisions", "APPROVED"],
  ["reject", "Reject leave", "decisions", "REJECTED"],
  ["withdraw", "Withdraw request", "withdraw", "CANCELLED"],
  ["cancel", "Request cancellation", "cancellation", "CANCELLATION_PENDING"],
] as const)
  test(`${label} uses a fresh review and preserves directory context`, async ({ page }) => {
    const api = await installLeaveActionsApi(page);
    if (intent === "cancel") api.approve();
    await page.goto(`${detail}&status=${api.record.status}&after=${leaveId(0, 1)}&historyAfter=4`);
    await page.getByRole("link", { name: label, exact: true }).click();
    await expect(page.getByRole("heading", { name: label, exact: true })).toBeVisible();
    await expect(
      page.getByRole("table", { name: "Submitted schedule (Asia/Jakarta)", exact: true }),
    ).toContainText("First half");
    expect(api.reads.at(-1)?.searchParams.has("historyAfter")).toBe(false);
    await page.getByLabel("Action reason", { exact: true }).fill("Reviewed schedule");
    await page.getByRole("button", { name: "Confirm action", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("Action recorded.");
    expect(api.record.status).toBe(status);
    expect(api.writes[0]).toMatchObject({
      company: companyIds[0],
      id: leaveId(0, 2),
      endpoint,
      body: { version: 0, reason: "Reviewed schedule" },
    });
    await page.getByRole("link", { name: "View current request", exact: true }).click();
    await expect(page).toHaveURL(new RegExp(`after=${leaveId(0, 1)}$`, "u"));
    await expect(page.getByRole("table", { name: "Request history", exact: true })).toContainText(
      "Reviewed schedule",
    );
  });
test("cancellation rejection and withdrawal keep their distinct meaning", async ({ page }) => {
  const api = await installLeaveActionsApi(page);
  api.cancel();
  await openAction(page, "reject");
  await expect(
    page.getByRole("heading", { name: "Reject cancellation", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("main")).toContainText("keeps the approved leave in place");
  await page.getByLabel("Action reason", { exact: true }).fill("Original leave still required");
  await page.getByRole("button", { name: "Confirm action", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Action recorded.");
  await expect(
    page.getByRole("heading", { name: "Reject cancellation", exact: true }),
  ).toBeVisible();
  expect(api.record.status).toBe("APPROVED");
  api.cancel();
  await openAction(page, "withdraw");
  await expect(
    page.getByRole("heading", { name: "Withdraw cancellation", exact: true }),
  ).toBeVisible();
  await page.getByLabel("Action reason", { exact: true }).fill("Keep original leave");
  await page.getByRole("button", { name: "Confirm action", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Action recorded.");
  expect(api.record.status).toBe("APPROVED");
});
test("an intermediate approval does not claim final leave approval", async ({ page }) => {
  const api = await installLeaveActionsApi(page);
  api.record.approval.stages.push(["20000000-0000-4000-8000-000000000002"]);
  await openAction(page);
  await expect(page.getByRole("main")).toContainText("Additional approval may be required");
  await page.getByRole("button", { name: "Confirm action", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Action recorded.");
  expect(api.record.status).toBe("PENDING");
  await page.getByRole("link", { name: "View current request", exact: true }).click();
  await expect(page.getByRole("region", { name: "Request details", exact: true })).toContainText(
    "Pending",
  );
});
test("rejection validates its reason before sending a command", async ({ page }) => {
  const api = await installLeaveActionsApi(page);
  await openAction(page, "reject");
  await page.getByRole("button", { name: "Confirm action", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Enter a reason");
  expect(api.writes).toEqual([]);
  await page.getByLabel("Action reason", { exact: true }).fill("Coverage not available");
  await page.getByRole("button", { name: "Confirm action", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Action recorded.");
});
test("lost decisions retain their receipt key through MFA and an advanced leave state", async ({
  page,
}) => {
  const api = await installLeaveActionsApi(page);
  await openAction(page);
  await page.getByLabel("Action reason", { exact: true }).fill("Original decision");
  const reads = api.reads.length;
  api.loseNext();
  await page.getByRole("button", { name: "Confirm action", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result is unconfirmed");
  api.identity.expireMfa();
  api.rejectNext("mfa_required", 403);
  await page.getByRole("button", { name: "Retry action", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const dialog = page.getByRole("dialog", { name: "Account verification", exact: true });
  await dialog.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await dialog.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  api.rejectNext("leave_not_pending");
  await page.getByRole("button", { name: "Retry action", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result is unconfirmed");
  await expect(page.getByRole("button", { name: "Confirm action", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Retry action", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Action recorded.");
  expect(api.commits).toBe(1);
  expect(api.writes).toHaveLength(4);
  expect(api.reads).toHaveLength(reads);
  for (const write of api.writes) expect(write).toEqual(api.writes[0]);
});
test("a stale version requires protected reload and a new reviewed command", async ({ page }) => {
  const api = await installLeaveActionsApi(page);
  await openAction(page);
  await page.getByLabel("Action reason", { exact: true }).fill("Old review");
  api.record.version += 1;
  await page.getByRole("button", { name: "Confirm action", exact: true }).click();
  await expect(page.getByRole("button", { name: "Confirm action", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Reload current version", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Stay on this page", exact: true })
    .click();
  await expect(page.getByLabel("Action reason", { exact: true })).toHaveValue("Old review");
  await page.getByRole("button", { name: "Reload current version", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Action reason", { exact: true })).toHaveValue("");
  await page.getByLabel("Action reason", { exact: true }).fill("Reviewed again");
  await page.getByRole("button", { name: "Confirm action", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Action recorded.");
  expect(api.writes[1]?.body.version).toBe(1);
  expect(api.writes[1]?.operation).not.toBe(api.writes[0]?.operation);
});
test("read permission alone cannot open a decision review", async ({ page }) => {
  const api = await installLeaveActionsApi(page, ["leave.read"]);
  await page.goto(detail);
  await expect(page.getByRole("region", { name: "Request details", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Approve leave", exact: true })).toHaveCount(0);
  const reads = api.reads.length;
  await page.goto(action("approve"));
  await expect(page.getByRole("alert")).toContainText("access");
  expect(api.reads).toHaveLength(reads);
  expect(api.writes).toEqual([]);
});
test("resource assignment is required even with the approval permission", async ({ page }) => {
  const api = await installLeaveActionsApi(page);
  api.record.availableActions = [];
  await page.goto(action("approve"));
  await expect(page.getByRole("alert")).toContainText("no longer available");
  await expect(page.getByRole("button", { name: "Confirm action", exact: true })).toHaveCount(0);
  expect(api.writes).toEqual([]);
});
test("company replacement discards a pending review and its previous filters", async ({ page }) => {
  const api = await installLeaveActionsApi(page);
  const held = api.hold("details");
  await page.goto(`${action("approve")}&after=${leaveId(0, 1)}`);
  await held.entered;
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  await expect(page.getByRole("table", { name: "Leave requests", exact: true })).toContainText(
    "South Employee",
  );
  held.release();
  await expect(page).toHaveURL(new RegExp(`/leave/requests\\?company=${companyIds[1]}$`, "u"));
  await expect(page.getByRole("main")).not.toContainText("North Employee");
});
test("departure during submission ignores its late receipt in the replacement company", async ({
  page,
}) => {
  const api = await installLeaveActionsApi(page);
  await openAction(page);
  const held = api.holdWrite();
  await page.getByRole("button", { name: "Confirm action", exact: true }).click();
  await held.entered;
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByRole("table", { name: "Leave requests", exact: true })).toContainText(
    "South Employee",
  );
  held.release();
  await expect(page.getByRole("main")).not.toContainText("Action recorded");
  expect(api.writes).toHaveLength(1);
  expect(api.writes[0]?.company).toBe(companyIds[0]);
});
test("Indonesian dark mobile review keeps its draft without reacquiring data", async ({ page }) => {
  const api = await installLeaveActionsApi(page);
  api.cancel();
  await page.setViewportSize({ width: 390, height: 844 });
  await openAction(page, "reject");
  await page.getByLabel("Action reason", { exact: true }).fill("Cuti masih diperlukan");
  const reads = api.reads.length;
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByRole("button", { name: "Muat versi terkini", exact: true })).toHaveCSS(
    "color",
    "rgb(255, 255, 255)",
  );
  await expect(page.getByRole("heading", { name: "Tolak pembatalan", exact: true })).toBeVisible();
  await expect(page.getByLabel("Alasan tindakan", { exact: true })).toHaveValue(
    "Cuti masih diperlukan",
  );
  expect(api.reads).toHaveLength(reads);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({
    path: "../../.work/dashboard-leave-action-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
});
