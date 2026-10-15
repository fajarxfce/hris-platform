import { expect, type Page, test } from "@playwright/test";
import { batchReviewWire } from "../src/features/leave/di/leave-batch-fixture";
import { companyIds } from "./identity-api";
import { batchId, batchPermissions, installLeaveBatchesApi } from "./leave-batches-api";

const path = "/leave/batches",
  query = `company=${companyIds[0]}`;
const picker = (page: Page) => page.getByRole("dialog", { name: "Choose leave type", exact: true });
const departure = (page: Page) =>
  page.getByRole("dialog", { name: "Leave this page?", exact: true });
async function prepare(page: Page, kind = "ACCRUAL") {
  await page.goto(`${path}/new?${query}&kind=${kind}`);
  await page.getByRole("button", { name: "Choose leave type", exact: true }).click();
  await picker(page).getByRole("button", { name: "Select: Annual leave", exact: true }).click();
  await page.getByRole("button", { name: "Review selection", exact: true }).click();
  await expect(page.getByRole("region", { name: "Review selection", exact: true })).toContainText(
    "Annual leave",
  );
}
test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(new Date("2026-10-15T03:00:00Z"));
});
test("reviewed accrual leads to employee outcomes and the scoped ledger", async ({ page }) => {
  const api = await installLeaveBatchesApi(page);
  await page.goto(`${path}?${query}`);
  await expect(page.getByRole("status")).toContainText("No leave processes.");
  await page.getByRole("link", { name: "Post accrual", exact: true }).click();
  await expect(page.getByLabel("Accrual month", { exact: true })).toHaveValue("2026-09");
  expect(api.reads.filter((url) => url.pathname.endsWith("/types"))).toHaveLength(0);
  await page.getByRole("button", { name: "Choose leave type", exact: true }).click();
  await picker(page).getByRole("button", { name: "Select: Annual leave", exact: true }).click();
  await page.getByRole("button", { name: "Review selection", exact: true }).click();
  await expect(page.getByRole("region", { name: "Review selection", exact: true })).toContainText(
    "September 2026",
  );
  await page.getByRole("button", { name: "Post accrual", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Check the leave type, period and reason.");
  expect(api.writes).toHaveLength(0);
  await page.getByLabel("Reason", { exact: true }).fill("September entitlement reviewed");
  await page.screenshot({ path: "/tmp/hris-leave-batches/review-desktop.png", fullPage: true });
  await page.getByRole("button", { name: "Post accrual", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Process submitted.");
  expect(api.writes[0]?.body).toMatchObject({
    month: "2026-09",
    employeeIds: batchReviewWire.employeeIds,
    expectedPolicyVersion: 4,
  });
  expect(api.committed).toBe(1);
  await page.getByRole("link", { name: "View process", exact: true }).click();
  await expect(page.getByRole("region", { name: "Process details", exact: true })).toContainText(
    "3 / 3",
  );
  const outcomes = page.getByRole("table", { name: "Employee results", exact: true });
  await expect(outcomes).toContainText("Skipped");
  await expect(outcomes).toContainText("not eligible");
  await expect(page.getByRole("link", { name: "Resume process", exact: true })).toHaveCount(0);
  await page.screenshot({ path: "/tmp/hris-leave-batches/outcomes-desktop.png", fullPage: true });
  await outcomes.getByRole("button", { name: "View balance: Alex Employee", exact: true }).click();
  await expect(page).toHaveURL(/\/leave\/employees\/.*\/balances\/.*year=2026/u);
  await expect.poll(() => api.ledgerReads.length).toBe(1);
  expect(api.identity.unhandled).toEqual([]);
});
test("year closing submits the reviewed year and explicit population", async ({ page }) => {
  const api = await installLeaveBatchesApi(page);
  await prepare(page, "YEAR_CLOSE");
  await expect(page.getByRole("region", { name: "Review selection", exact: true })).toContainText(
    "2025",
  );
  await page.getByLabel("Reason", { exact: true }).fill("Year-end balances reviewed");
  await page.getByRole("button", { name: "Close leave year", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Process submitted.");
  expect(api.writes[0]).toMatchObject({
    action: "year-close-batches",
    body: { year: 2025, expectedPolicyVersion: 4, employeeIds: batchReviewWire.employeeIds },
  });
  expect(api.writes[0]?.body).not.toHaveProperty("month");
  expect(api.committed).toBe(1);
});
test("type discovery is explicit and bounded rather than scanning every page", async ({ page }) => {
  const api = await installLeaveBatchesApi(page);
  await page.goto(`${path}/new?${query}&kind=ACCRUAL`);
  await page.getByRole("button", { name: "Choose leave type", exact: true }).click();
  await expect(picker(page).getByRole("table").getByRole("row")).toHaveCount(21);
  expect(api.reads).toHaveLength(1);
  await picker(page).getByRole("button", { name: "Next page", exact: true }).click();
  await expect(picker(page).getByRole("table").getByRole("row")).toHaveCount(3);
  expect(api.reads).toHaveLength(2);
  expect(api.reads[1]?.searchParams.get("after")).toBe("BATCH_020");
  await picker(page).getByRole("button", { name: "Select: Leave type 22", exact: true }).click();
  await expect(page.getByRole("main")).toContainText("Leave type 22");
});
for (const missing of ["leave.read", "leave.accrual.post"]) {
  test(`accrual requires ${missing} before any acquisition`, async ({ page }) => {
    const api = await installLeaveBatchesApi(
      page,
      batchPermissions.filter((permission) => permission !== missing),
    );
    await page.goto(`${path}/new?${query}&kind=ACCRUAL`);
    await expect(page.getByRole("alert")).toContainText(/permission|access/iu);
    expect(api.reads).toHaveLength(0);
    expect(api.writes).toHaveLength(0);
    await expect(page.getByRole("button", { name: "Choose leave type", exact: true })).toHaveCount(
      0,
    );
  });
}
test("read access offers history without either operation", async ({ page }) => {
  const api = await installLeaveBatchesApi(page, ["leave.read"]);
  api.records.get(companyIds[0])?.push(api.seed());
  await page.goto(`${path}?${query}`);
  await expect(page.getByRole("table", { name: "Leave processing", exact: true })).toContainText(
    "Incomplete",
  );
  await expect(page.getByRole("link", { name: "Post accrual", exact: true })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Close leave year", exact: true })).toHaveCount(0);
});
test("a changed policy requires an explicit new review before another command", async ({
  page,
}) => {
  const api = await installLeaveBatchesApi(page);
  await prepare(page);
  const type = api.types[0];
  if (!type) throw Error("Missing policy");
  type.version++;
  await page.getByLabel("Reason", { exact: true }).fill("Review one");
  await page.getByRole("button", { name: "Post accrual", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("policy changed");
  await expect(page.getByRole("button", { name: "Post accrual", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Change selection", exact: true }).click();
  await page.getByRole("button", { name: "Review selection", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Review two");
  await page.getByRole("button", { name: "Post accrual", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Process submitted.");
  expect(api.writes).toHaveLength(2);
  expect(api.writes[1]?.body.expectedPolicyVersion).toBe(5);
  expect(api.writes[0]?.operation).not.toBe(api.writes[1]?.operation);
  expect(api.committed).toBe(1);
});
for (const action of ["start", "resume"] as const) {
  test(`uncertain ${action} recovers its original command through MFA and conflict`, async ({
    page,
  }) => {
    const api = await installLeaveBatchesApi(page);
    if (action === "start") await prepare(page);
    else {
      api.records.get(companyIds[0])?.push(api.seed());
      await page.goto(`${path}/${batchId}/resume?${query}`);
    }
    await page.getByLabel("Reason", { exact: true }).fill("Original reviewed command");
    api.loseCommand();
    await page
      .getByRole("button", {
        name: action === "start" ? "Post accrual" : "Resume process",
        exact: true,
      })
      .click();
    await expect(page.getByRole("status")).toContainText("The result is unconfirmed.");
    api.identity.expireMfa();
    api.rejectCommand("mfa_required", 403);
    await page.getByRole("button", { name: "Retry original command", exact: true }).click();
    await page
      .getByRole("alert")
      .getByRole("button", { name: "Account verification", exact: true })
      .click();
    const dialog = page.getByRole("dialog", { name: "Account verification", exact: true });
    await dialog.getByLabel("Authenticator code", { exact: true }).fill("123456");
    await dialog.getByRole("button", { name: "Verify", exact: true }).click();
    await expect(dialog).toHaveCount(0);
    api.rejectCommand("leave_batch_policy_changed");
    await page.getByRole("button", { name: "Retry original command", exact: true }).click();
    await expect(page.getByRole("alert")).toContainText("policy changed");
    await page.getByRole("button", { name: "Retry original command", exact: true }).click();
    await expect(page.getByRole("status")).toContainText(
      action === "start" ? "Process submitted." : "Process resumed.",
    );
    expect(api.writes).toHaveLength(4);
    expect(api.committed).toBe(1);
    for (const write of api.writes) {
      expect(write.operation).toBe(api.writes[0]?.operation);
      expect(write.body).toEqual(api.writes[0]?.body);
    }
    await page.getByRole("link", { name: "View process", exact: true }).click();
    await expect(page.getByRole("table", { name: "Employee results", exact: true })).toContainText(
      "Alex Employee",
    );
    if (action === "resume") {
      const item = api.records.get(companyIds[0])?.[0];
      expect(item?.counts.completed).toBe(2);
      expect(item?.attempts).toHaveLength(2);
      await expect(
        page.getByRole("region", { name: "Process details", exact: true }),
      ).toContainText("Queued");
    }
  });
}
test("server recovery availability gates the resume form", async ({ page }) => {
  const api = await installLeaveBatchesApi(page),
    item = api.seed();
  item.availableActions = [];
  api.records.get(companyIds[0])?.push(item);
  await page.goto(`${path}/${batchId}?${query}`);
  await expect(page.getByRole("link", { name: "Resume process", exact: true })).toHaveCount(0);
  await page.goto(`${path}/${batchId}/resume?${query}`);
  await expect(page.getByRole("status")).toContainText("cannot be resumed");
  await expect(page.getByLabel("Reason", { exact: true })).toHaveCount(0);
  expect(api.writes).toHaveLength(0);
});
test("bounded result pages retain the originating directory cursor", async ({ page }) => {
  const api = await installLeaveBatchesApi(page),
    item = api.seed();
  api.complete(item, 23);
  api.records.get(companyIds[0])?.push(item);
  const before = "40000000-abcd-4000-8000-000000000000";
  await page.goto(`${path}/${batchId}?${query}&after=${before}`);
  const table = page.getByRole("table", { name: "Employee results", exact: true });
  await expect(table.getByRole("row")).toHaveCount(21);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(4);
  await expect(table).toContainText("Employee 23");
  expect(api.reads.at(-1)?.searchParams.get("after")).toBe("20");
  await page.getByRole("link", { name: "Back to leave processing", exact: true }).click();
  await expect(page).toHaveURL(`${new URL(page.url()).origin}${path}?${query}&after=${before}`);
  expect(page.url()).not.toContain("resultAfter");
});
test("company departure discards a late committed response without crossing scope", async ({
  page,
}) => {
  const api = await installLeaveBatchesApi(page);
  await prepare(page);
  await page.getByLabel("Reason", { exact: true }).fill("North company review");
  const held = api.holdCommand();
  try {
    await page.getByRole("button", { name: "Post accrual", exact: true }).click();
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("No leave processes.");
    held.release();
    await expect.poll(() => api.committed).toBe(1);
    await expect(page.getByRole("main")).not.toContainText("North company review");
    await expect(page.getByRole("main")).not.toContainText("Process submitted.");
    expect(api.writes).toHaveLength(1);
  } finally {
    held.release();
  }
});
test("denied detail refresh erases retained results and replacement aborts late reads", async ({
  page,
}) => {
  const api = await installLeaveBatchesApi(page);
  api.records.get(companyIds[0])?.push(api.seed());
  await page.goto(`${path}/${batchId}?${query}`);
  await expect(page.getByRole("table", { name: "Employee results", exact: true })).toBeVisible();
  api.rejectReads("leave_batch_not_found");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("This leave process is unavailable.");
  await expect(page.getByRole("table", { name: "Employee results", exact: true })).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText("PRIVATE");
  api.rejectReads(null);
  const held = api.holdRead();
  try {
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("status")).toContainText("No leave processes.");
    held.release();
    await expect(page.getByRole("main")).not.toContainText("Alex Employee");
  } finally {
    held.release();
  }
});
test("Indonesian narrow dark review retains input through theme and locale changes", async ({
  page,
}) => {
  const api = await installLeaveBatchesApi(page);
  await prepare(page);
  await page.getByLabel("Reason", { exact: true }).fill("Hak cuti September ditinjau");
  const reads = api.reads.length;
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue(
    "Hak cuti September ditinjau",
  );
  expect(api.reads).toHaveLength(reads);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: "/tmp/hris-leave-batches/review-mobile-id.png", fullPage: true });
  await page.evaluate(() => {
    document.documentElement.style.fontSize = "200%";
  });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
