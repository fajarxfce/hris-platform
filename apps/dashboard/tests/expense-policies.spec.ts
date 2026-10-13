import { expect, type Page, test } from "@playwright/test";
import { installExpensePolicyEditorApi } from "./expense-policy-editor-api";
import { expensePolicyId } from "./fixtures/expense-policies";
import { companyIds } from "./identity-api";

const query = `company=${companyIds[0]}`,
  directory = `/expenses/policies?${query}`;
const detail = `/expenses/policies/${expensePolicyId()}?${query}`;
const editor = `/expenses/policies/${expensePolicyId()}/edit?${query}`;
async function openEditor(page: Page) {
  await page.goto(editor);
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("North travel 01");
  await page.getByLabel("Reason", { exact: true }).fill("Annual cost review");
}

test("catalog paging and status filters retain company scope through details", async ({ page }) => {
  await installExpensePolicyEditorApi(page);
  await page.goto(directory);
  const table = page.getByRole("table", { name: "Expense policies", exact: true });
  await expect(table.getByRole("row")).toHaveCount(21);
  expect(
    await table.evaluate((element) => {
      const viewport = element.parentElement;
      return viewport !== null && viewport.scrollWidth <= viewport.clientWidth;
    }),
  ).toBe(true);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(4);
  await page.getByRole("button", { name: "View: North travel 21", exact: true }).click();
  await expect(page.getByRole("region", { name: "Latest saved revision" })).toBeVisible();
  await page.getByRole("link", { name: "Back to policies", exact: true }).click();
  await expect(page).toHaveURL(`${directory}&after=EXPENSE020`);
  await page.getByRole("combobox", { name: "Status", exact: true }).selectOption("false");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(13);
  await expect(page).toHaveURL(`${directory}&active=false`);
});

test("history pages advance chronologically while editing always loads the latest saved policy", async ({
  page,
}) => {
  const api = await installExpensePolicyEditorApi(page);
  await page.goto(detail);
  const history = page.getByRole("table", { name: "Policy history", exact: true });
  await expect(history.getByRole("row")).toHaveCount(21);
  await page.getByRole("button", { name: "View: Revision 0", exact: true }).click();
  await expect(page.getByRole("region", { name: "Revision 0", exact: true })).toContainText(
    "400,000.50",
  );
  const selectedBounds = await page
    .getByRole("region", { name: "Revision 0", exact: true })
    .boundingBox();
  const headerBounds = await page.locator(".app-topbar").boundingBox();
  expect(selectedBounds?.y).toBeGreaterThanOrEqual(
    (headerBounds?.y ?? 0) + (headerBounds?.height ?? 0),
  );
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(history.getByRole("row")).toHaveCount(5);
  await expect(page).toHaveURL(`${detail}&historyAfter=19`);
  await page.getByRole("link", { name: "Edit policy", exact: true }).click();
  await expect(page.getByLabel("Code", { exact: true })).toHaveAttribute("readonly", "");
  await expect(page.getByLabel("Maximum per line (IDR)", { exact: true })).toHaveValue("500000.25");
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed current limits");
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Policy saved.");
  expect(api.writes[0]?.body).toMatchObject({ code: "EXPENSE001", expectedVersion: 23 });
});

test("category creation preserves exact IDR limits and evidence requirements", async ({ page }) => {
  const api = await installExpensePolicyEditorApi(page);
  await page.goto(directory);
  await page.getByRole("link", { name: "Create category", exact: true }).click();
  await page.getByLabel("Code", { exact: true }).fill("travel_ui");
  await page.getByLabel("Name", { exact: true }).fill("Business travel");
  await page.getByLabel("Effective from", { exact: true }).fill("2027-01-01");
  await page.getByLabel("Maximum per line (IDR)", { exact: true }).fill("999999999999.98");
  await page.getByLabel("Maximum per claim (IDR)", { exact: true }).fill("999999999999.99");
  await page.getByLabel("Maximum transaction age (days)", { exact: true }).fill("45");
  await page.getByRole("checkbox", { name: "Cost center required", exact: true }).check();
  await page.getByRole("checkbox", { name: "Fixed term", exact: true }).uncheck();
  await page.getByLabel("Reason", { exact: true }).fill("Travel reimbursement limits");
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Policy saved.");
  expect(api.writes).toHaveLength(1);
  expect(api.writes[0]?.body).toMatchObject({
    code: "TRAVEL_UI",
    expectedVersion: null,
    maximumLineAmount: "999999999999.98",
    maximumClaimAmount: "999999999999.99",
    maximumAgeDays: 45,
    receiptRequired: true,
    costCenterRequired: true,
    allowedContracts: ["PERMANENT"],
  });
  await page.getByRole("link", { name: "View policy", exact: true }).click();
  await page.getByRole("button", { name: "View: Revision 0", exact: true }).click();
  await expect(page.getByRole("region", { name: "Revision 0", exact: true })).toContainText(
    "999,999,999,999.99",
  );
});

test("validation keeps input and rejects invalid amounts and contracts before HTTP", async ({
  page,
}) => {
  const api = await installExpensePolicyEditorApi(page);
  await openEditor(page);
  await page.getByLabel("Maximum per line (IDR)", { exact: true }).fill("1.001");
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByLabel("Maximum per line (IDR)", { exact: true })).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Annual cost review");
  await page.getByLabel("Maximum per line (IDR)", { exact: true }).fill("100.01");
  await page.getByLabel("Maximum per claim (IDR)", { exact: true }).fill("100.00");
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(
    page.getByText("The claim limit must be at least the line limit.", { exact: true }),
  ).toBeVisible();
  await page.getByRole("checkbox", { name: "Permanent", exact: true }).uncheck();
  await page.getByRole("checkbox", { name: "Fixed term", exact: true }).uncheck();
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByText("Select at least one contract type.", { exact: true })).toBeVisible();
  expect(api.writes).toHaveLength(0);
});

test("lost save keeps one operation through a later rejection and explicit recovery", async ({
  page,
}) => {
  const api = await installExpensePolicyEditorApi(page);
  await openEditor(page);
  api.dropNext();
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save result is unconfirmed");
  await expect(page.getByLabel("Name", { exact: true })).toHaveAttribute("readonly", "");
  api.rejectNext("stale_version");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("button", { name: "Retry save", exact: true })).toBeEnabled();
  await expect(page.getByRole("button", { name: "Load current policy", exact: true })).toHaveCount(
    0,
  );
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Policy saved.");
  expect(api.writes).toHaveLength(3);
  for (const write of api.writes) expect(write).toEqual(api.writes[0]);
});

test("a definite conflict needs protected reload before saving a new revision", async ({
  page,
}) => {
  const api = await installExpensePolicyEditorApi(page);
  await openEditor(page);
  api.advance(companyIds[0], expensePolicyId());
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await page.getByRole("button", { name: "Load current policy", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("Other writer policy");
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed current policy");
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Policy saved.");
  expect(api.writes.map((write) => write.body.expectedVersion)).toEqual([23, 24]);
  expect(api.writes[0]?.operation).not.toBe(api.writes[1]?.operation);
});

test("company replacement cancels a pending review and drops its private content", async ({
  page,
}) => {
  const api = await installExpensePolicyEditorApi(page),
    held = api.hold("details");
  try {
    await page.goto(editor);
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("table", { name: "Expense policies", exact: true })).toContainText(
      "South travel 01",
    );
    held.release();
    await expect(page.getByRole("main")).not.toContainText("North travel");
  } finally {
    held.release();
  }
});

test("an unauthorized read cannot render historical reasons", async ({ page }) => {
  const api = await installExpensePolicyEditorApi(page);
  api.identity.setPermissions(["company.read", "expenses.read"]);
  await page.goto(detail);
  await expect(page.getByRole("alert")).toContainText("You do not have access");
  await expect(page.getByRole("link", { name: "Expense policies", exact: true })).toHaveCount(0);
  expect(api.reads).toHaveLength(0);
});

test("narrow dark layout keeps draft values across locale changes", async ({ page }) => {
  const api = await installExpensePolicyEditorApi(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await openEditor(page);
  await page.getByLabel("Maximum per line (IDR)", { exact: true }).fill("999999999999.98");
  const reads = api.reads.length;
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByLabel("Maksimal per item (IDR)", { exact: true })).toHaveValue(
    "999999999999.98",
  );
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue("Annual cost review");
  await expect(page.getByLabel("Nama", { exact: true })).toHaveCSS("color", "rgb(255, 255, 255)");
  expect(api.reads).toHaveLength(reads);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-expense-policy-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
});
