import { expect, test } from "@playwright/test";
import { approvalId, installApprovalsApi } from "./approvals-api";
import { companyIds } from "./identity-api";

const list = `/approvals?company=${companyIds[0]}`;
const details = `/approvals/${approvalId(0, 2)}?company=${companyIds[0]}`;

test("approval inbox pages and blocked assignment details retain company and cursor context", async ({
  page,
}) => {
  const api = await installApprovalsApi(page);
  await page.goto(list);
  const inbox = page.getByRole("table", { name: "Approval inbox", exact: true });
  await expect(inbox.getByRole("row")).toHaveCount(21);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(inbox.getByRole("row")).toHaveCount(3);
  await expect(page.getByRole("button", { name: "Next page", exact: true })).toBeDisabled();
  await page
    .getByRole("button", {
      name: "View: Leave · 40000000-0000-4000-8000-000000000021",
      exact: true,
    })
    .click();
  await expect(page.getByRole("region", { name: "Request details", exact: true })).toContainText(
    "Pending",
  );
  await page.getByRole("link", { name: "Back to approvals", exact: true }).click();
  await expect(inbox.getByRole("row")).toHaveCount(3);
  await page.getByRole("button", { name: "First page", exact: true }).click();
  await page
    .getByRole("button", {
      name: "View: Leave · 40000000-0000-4000-8000-000000000002",
      exact: true,
    })
    .click();
  await expect(page.getByRole("main")).toContainText("This request needs an assigned approver");
  const stages = page.getByRole("table", { name: "Approval stages", exact: true });
  await expect(stages).toContainText("Approved");
  await expect(stages).toContainText("Assignment required");
  expect(api.identity.unhandled).toEqual([]);
});
test("inbox access is checked before I/O but author detail authorization remains server-owned", async ({
  page,
}) => {
  const api = await installApprovalsApi(page, []);
  await page.goto(list);
  await expect(page.getByRole("alert")).toContainText("You do not have access");
  await expect(page.getByRole("link", { name: "Approvals", exact: true })).toHaveCount(0);
  expect(api.reads).toEqual([]);
  await page.goto(details);
  await expect(page.getByRole("region", { name: "Request details", exact: true })).toBeVisible();
  expect(api.reads.every((url) => url.pathname.endsWith(approvalId(0, 2)))).toBe(true);
});
test("invalid cursors are rejected locally and the first-page action recovers", async ({
  page,
}) => {
  const api = await installApprovalsApi(page);
  await page.goto(`${list}&after=invalid`);
  await expect(page.getByRole("alert")).toContainText("This page link is invalid");
  expect(api.reads).toEqual([]);
  await page.getByRole("button", { name: "First page", exact: true }).click();
  await expect(page.getByRole("table")).toBeVisible();
  await page.goto(`/approvals/invalid?company=${companyIds[0]}`);
  await expect(page.getByRole("alert")).toContainText("The approval request was not found");
  expect(api.reads.some((url) => url.pathname.endsWith("invalid"))).toBe(false);
});
for (const kind of ["inbox", "request"] as const) {
  test(`a pending approval ${kind} cannot populate another company`, async ({ page }) => {
    const api = await installApprovalsApi(page);
    const pending = api.hold(kind);
    try {
      await page.goto(kind === "inbox" ? list : details);
      await pending.entered;
      await page
        .getByRole("combobox", { name: "Company", exact: true })
        .selectOption(companyIds[1]);
      await expect(page).toHaveURL(`/approvals?company=${companyIds[1]}`);
      pending.release();
      await expect(page.getByRole("table")).toContainText("40000000-0000-4000-8000-000000000101");
      await expect(page.getByRole("main")).not.toContainText(
        "40000000-0000-4000-8000-000000000002",
      );
      expect(
        api.reads
          .filter((url) => url.pathname.includes(companyIds[1]))
          .every((url) => !url.searchParams.has("after") && url.pathname.endsWith("approvals")),
      ).toBe(true);
    } finally {
      pending.release();
    }
  });
}
test("revoked request access clears the stage snapshot and sanitized errors recover explicitly", async ({
  page,
}) => {
  const api = await installApprovalsApi(page);
  await page.goto(details);
  await expect(page.getByRole("table")).toBeVisible();
  api.fail("approval_not_found");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("The approval request was not found");
  await expect(page.getByRole("table")).toHaveCount(0);
  await expect(page.getByRole("region", { name: "Request details", exact: true })).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText("PRIVATE TECHNICAL");
  api.fail(null);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("table")).toBeVisible();
});
for (const path of [list, details]) {
  test(`invalid response data is not displayed at ${path}`, async ({ page }) => {
    const api = await installApprovalsApi(page);
    api.malformed();
    await page.goto(path);
    await expect(page.getByRole("alert")).toContainText("The service returned an invalid response");
    await expect(page.getByRole("table")).toHaveCount(0);
  });
}
test("approval details support Indonesian, dark mode and contained mobile tables", async ({
  page,
}) => {
  await installApprovalsApi(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(details);
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Permintaan approval", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("table", { name: "Tahap approval", exact: true })).toContainText(
    "Perlu penugasan",
  );
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
    .toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-approval-mobile.png",
    animations: "disabled",
  });
});
