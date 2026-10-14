import { expect, type Page, test } from "@playwright/test";
import { companyIds } from "./identity-api";
import { installPayrollPoliciesApi } from "./payroll-policies-api";

const query = `company=${companyIds[0]}&month=2026-06`,
  policy = `/payroll/policy?${query}`,
  editor = `/payroll/policy/edit?${query}`;
async function openEditor(page: Page) {
  await page.goto(editor);
  await expect(page.getByLabel("Minimum monthly wage (IDR)", { exact: true })).toHaveValue(
    "4100000.25",
  );
  await page.getByLabel("Reason", { exact: true }).fill("Annual payroll policy review");
}
test("month selection and history remain separate from the latest editable configuration", async ({
  page,
}) => {
  const api = await installPayrollPoliciesApi(page);
  await page.goto(policy);
  await expect(page.getByRole("region", { name: "Effective policy", exact: true })).toContainText(
    "4,000,000.50",
  );
  const table = page.getByRole("table", { name: "Policy history", exact: true });
  await expect(table.getByRole("row")).toHaveCount(21);
  const reads = api.reads.length;
  await page.getByRole("button", { name: "View: Revision 0", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText("Company policy review 0");
  await expect(page.getByRole("dialog")).toContainText("4,000,000.50");
  expect(api.reads).toHaveLength(reads);
  await page.getByRole("button", { name: "Close details", exact: true }).click();
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(5);
  await expect(page).toHaveURL(`${policy}&historyAfter=19`);
  await page.getByRole("link", { name: "New revision", exact: true }).click();
  await expect(page.getByLabel("Minimum monthly wage (IDR)", { exact: true })).toHaveValue(
    "4100000.25",
  );
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed newest policy");
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Policy saved.");
  expect(api.writes[0]?.body.expectedVersion).toBe(23);
});
test("an uncovered month retains the configuration and its history", async ({ page }) => {
  await installPayrollPoliciesApi(page);
  await page.goto(policy);
  await page.getByLabel("Payroll month", { exact: true }).fill("2028-01");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("No policy covers this month.");
  await expect(page.getByRole("table", { name: "Policy history", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "View configuration", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText("4,100,000.25");
});
test("initial setup has no prefilled statutory caps and preserves decimal inputs", async ({
  page,
}) => {
  const api = await installPayrollPoliciesApi(page);
  api.records.delete(companyIds[0]);
  await page.goto(policy);
  await expect(page.getByRole("status")).toContainText("No payroll policy configured.");
  await page.getByRole("link", { name: "Configure policy", exact: true }).click();
  await expect(page.getByLabel("Pension contribution wage cap (IDR)", { exact: true })).toHaveValue(
    "",
  );
  await page.getByLabel("Minimum monthly wage (IDR)", { exact: true }).fill("49999999999.99");
  await page
    .getByLabel("Health contribution wage cap (IDR)", { exact: true })
    .fill("50000000000.00");
  await page.getByLabel("Pension contribution wage cap (IDR)", { exact: true }).fill("10000000.50");
  await page
    .getByLabel("Review references", { exact: true })
    .fill("https://example.test/reviewed-fixture");
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed company setup");
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Policy saved.");
  expect(api.writes[0]?.body).toMatchObject({
    expectedVersion: null,
    minimumMonthlyWage: "49999999999.99",
    healthWageCap: "50000000000.00",
  });
});
test("invalid periods, money and references are rejected without a write", async ({ page }) => {
  const api = await installPayrollPoliciesApi(page);
  await openEditor(page);
  await page.getByLabel("Effective through", { exact: true }).fill("2028-01");
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByLabel("Effective through", { exact: true })).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  await page.getByLabel("Effective through", { exact: true }).fill("2027-12");
  await page.getByLabel("Minimum monthly wage (IDR)", { exact: true }).fill("1.001");
  await page.getByLabel("Review references", { exact: true }).fill("javascript:alert(1)");
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByLabel("Minimum monthly wage (IDR)", { exact: true })).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  await expect(page.getByLabel("Review references", { exact: true })).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue(
    "Annual payroll policy review",
  );
  expect(api.writes).toHaveLength(0);
});
test("lost save keeps the original payload through a rejected retry", async ({ page }) => {
  const api = await installPayrollPoliciesApi(page);
  await openEditor(page);
  api.dropNext();
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save result is unconfirmed");
  await expect(page.getByLabel("Reason", { exact: true })).toHaveAttribute("readonly", "");
  api.failNext("stale_version");
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
test("conflict recovery protects the draft before loading the new head", async ({ page }) => {
  const api = await installPayrollPoliciesApi(page);
  await openEditor(page);
  api.advance(companyIds[0]);
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await page.getByRole("button", { name: "Load current policy", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Minimum monthly wage (IDR)", { exact: true })).toHaveValue(
    "4300000.75",
  );
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed concurrent policy change");
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Policy saved.");
  expect(api.writes.map((write) => write.body.expectedVersion)).toEqual([23, 24]);
  expect(api.writes[0]?.operation).not.toBe(api.writes[1]?.operation);
});
test("company replacement rejects a late policy response", async ({ page }) => {
  const api = await installPayrollPoliciesApi(page),
    held = api.holdRead();
  try {
    await page.goto(editor);
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("heading", { name: "Payroll policy", exact: true })).toBeVisible();
    await expect(page).toHaveURL(new RegExp(`company=${companyIds[1]}`, "u"));
    await page.getByRole("button", { name: "View configuration", exact: true }).click();
    await expect(page.getByRole("dialog")).toContainText("6,200,000.00");
    held.release();
    await expect(page.getByRole("main")).not.toContainText("4,100,000.25");
  } finally {
    held.release();
  }
});
test("read permission exposes no policy editing or salary administration", async ({ page }) => {
  const api = await installPayrollPoliciesApi(page, ["payroll.read"]);
  await page.goto(policy);
  await expect(page.getByRole("region", { name: "Effective policy", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "New revision", exact: true })).toHaveCount(0);
  const reads = api.reads.length;
  await page.goto(editor);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.reads).toHaveLength(reads);
  expect(api.writes).toHaveLength(0);
});
test("locale and theme keep entered values without refetching and fit a narrow window", async ({
  page,
}) => {
  const api = await installPayrollPoliciesApi(page);
  await openEditor(page);
  await page.getByLabel("Minimum monthly wage (IDR)", { exact: true }).fill("4100000.99");
  const reads = api.reads.length;
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByLabel("Upah minimum bulanan (IDR)", { exact: true })).toHaveValue(
    "4100000.99",
  );
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue(
    "Annual payroll policy review",
  );
  expect(api.reads).toHaveLength(reads);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-payroll-policy-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
});
