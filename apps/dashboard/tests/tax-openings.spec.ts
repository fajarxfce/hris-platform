import { expect, type Page, test } from "@playwright/test";
import { openingEmployee, openingYear } from "./fixtures/tax-openings";
import { companyIds } from "./identity-api";
import { installTaxOpeningsApi } from "./tax-openings-api";

const directory = `/payroll/tax-openings?company=${companyIds[0]}&year=${openingYear}`,
  detail = `/payroll/tax-openings/${openingEmployee}?company=${companyIds[0]}&year=${openingYear}`,
  edit = `/payroll/tax-openings/${openingEmployee}/edit?company=${companyIds[0]}&year=${openingYear}`;
async function fillBasic(page: Page) {
  await page.goto(edit);
  await expect(page.getByLabel("Taxable gross income (IDR)", { exact: true })).toBeVisible();
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed opening history");
}

test("directory scopes its heads by year and history tabs reuse the review", async ({ page }) => {
  const api = await installTaxOpeningsApi(page);
  await page.goto(directory);
  await page
    .getByRole("button", { name: "Open history: Tax Opening Employee", exact: true })
    .click();
  await expect(page.getByRole("region", { name: "Revision 2", exact: true })).toContainText(
    "Reviewed June history",
  );
  const reads = api.reads.length;
  await page.getByRole("tab", { name: "History", exact: true }).click();
  expect(api.reads).toHaveLength(reads);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.getByRole("button", { name: "View revision: Revision 0", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText("Prepare opening revision 0");
  await page.getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("tab", { name: "Overview", exact: true }).click();
  expect(api.reads).toHaveLength(reads + 1);
  await page
    .getByRole("main")
    .getByRole("link", { name: "Opening tax history", exact: true })
    .click();
  await page.getByRole("combobox", { name: "Status", exact: true }).selectOption("VERIFIED");
  await page.getByRole("button", { name: "Apply filters", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("No employees match these filters.");
  await page.getByRole("combobox", { name: "Status", exact: true }).selectOption("UNPREPARED");
  await page.getByLabel("Tax year", { exact: true }).fill("2025");
  await page.getByRole("button", { name: "Apply filters", exact: true }).click();
  await expect(page.getByRole("table", { name: "Opening tax history", exact: true })).toContainText(
    "Not prepared",
  );
  await page
    .getByRole("button", { name: "Open history: Tax Opening Employee", exact: true })
    .click();
  await expect(page.getByRole("status")).toContainText("No opening history has been prepared.");
});

test("new opening requires explicit declarations and preserves annual decimal amounts", async ({
  page,
}) => {
  const api = await installTaxOpeningsApi(page, ["payroll.calculate"], true);
  await page.goto(edit);
  for (const label of [
    "History through",
    "Tax residency",
    "PTKP status",
    "Taxable gross income (IDR)",
    "Employment months",
  ])
    await expect(page.getByLabel(label, { exact: true })).toHaveValue("");
  await page.getByLabel("History through", { exact: true }).selectOption("6");
  await page.getByLabel("Tax residency", { exact: true }).selectOption("RESIDENT");
  await page.getByLabel("PTKP status", { exact: true }).selectOption("K0");
  for (const [label, value] of [
    ["Taxable gross income (IDR)", "60000000000.25"],
    ["Retirement contributions (IDR)", "600000.25"],
    ["Qualified donations (IDR)", "0"],
    ["Tax withheld (IDR)", "1500000"],
    ["Previous employer net income (IDR)", "10000000.50"],
    ["Previous employer tax withheld (IDR)", "50000.25"],
    ["Employment months", "6"],
    ["Review reference", "Reviewed June history"],
    ["Reason", "Prepare reviewed annual totals"],
  ] as const)
    await page.getByLabel(label, { exact: true }).fill(value);
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Opening saved.");
  expect(api.writes[0]?.body).toMatchObject({
    expectedVersion: null,
    expectedEmploymentVersion: 7,
    terms: {
      throughMonth: 6,
      residency: "RESIDENT",
      ptkp: "K0",
      history: {
        taxableGross: "60000000000.25",
        employmentMonths: 6,
        previousEmployerNet: "10000000.50",
        previousEmployerWithheld: "50000.25",
      },
    },
  });
  await page.getByRole("link", { name: "View history", exact: true }).click();
  await expect(
    page.getByRole("region", { name: "Year-to-date history", exact: true }),
  ).toContainText("60,000,000,000.25");
});

test("inconsistent declarations remain editable without sending a command", async ({ page }) => {
  const api = await installTaxOpeningsApi(page);
  await fillBasic(page);
  await page.getByLabel("Employment months", { exact: true }).fill("0");
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(
    page.getByText("Check the income, deductions and employment-month totals.", { exact: true }),
  ).toBeVisible();
  expect(api.writes).toHaveLength(0);
  await page.getByLabel("Employment months", { exact: true }).fill("6");
  await page.getByLabel("Tax residency", { exact: true }).selectOption("NON_RESIDENT");
  await page.getByLabel("Previous employer net income (IDR)", { exact: true }).fill("100");
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(
    page.getByText("Previous employer reconciliation does not apply to non-residents.", {
      exact: true,
    }),
  ).toBeVisible();
  expect(api.writes).toHaveLength(0);
  await page.getByLabel("Previous employer net income (IDR)", { exact: true }).fill("0");
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Opening saved.");
  expect(api.writes).toHaveLength(1);
});

test("lost save response retains the original command after a rejected retry", async ({ page }) => {
  const api = await installTaxOpeningsApi(page);
  await fillBasic(page);
  api.dropNext();
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result is unconfirmed.");
  api.failNext("stale_version");
  await page.getByRole("button", { name: "Retry request", exact: true }).click();
  await expect(page.getByRole("button", { name: "Retry request", exact: true })).toBeEnabled();
  await page.getByRole("button", { name: "Retry request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Opening saved.");
  expect(api.writes).toHaveLength(3);
  expect(api.writes[1]).toEqual(api.writes[0]);
  expect(api.writes[2]).toEqual(api.writes[0]);
});

test("changed employment requires protected reload before a new command", async ({ page }) => {
  const api = await installTaxOpeningsApi(page);
  await fillBasic(page);
  api.advanceEmployment();
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(page.getByRole("button", { name: "Reload review", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  await page.getByLabel("Reason", { exact: true }).fill("Review updated employment");
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Opening saved.");
  expect(api.writes[1]?.body.expectedEmploymentVersion).toBe(8);
  expect(api.writes[1]?.operation).not.toBe(api.writes[0]?.operation);
});

test("independent verification displays the observed revision and recovers its receipt", async ({
  page,
}) => {
  const api = await installTaxOpeningsApi(page, ["payroll.review"]);
  await page.goto(detail);
  await expect(page.getByRole("link", { name: "Edit opening", exact: true })).toHaveCount(0);
  await page.getByRole("link", { name: "Verify opening", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Verify opening history", exact: true }),
  ).toBeVisible();
  await page.getByLabel("Reason", { exact: true }).fill("Independent review of June history");
  api.dropNext();
  await page.getByRole("button", { name: "Verify opening", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result is unconfirmed.");
  await page.getByRole("button", { name: "Retry request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Opening verified.");
  expect(api.writes[0]?.body).toEqual({
    expectedVersion: 2,
    reason: "Independent review of June history",
  });
  expect(api.writes[1]).toEqual(api.writes[0]);
  await page.getByRole("link", { name: "View history", exact: true }).click();
  await expect(page.getByRole("link", { name: "Verify opening", exact: true })).toHaveCount(0);
});

test("locale and theme retain the draft on a narrow viewport", async ({ page }) => {
  const api = await installTaxOpeningsApi(page);
  await fillBasic(page);
  const reads = api.reads.length;
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue("Reviewed opening history");
  expect(api.reads).toHaveLength(reads);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-tax-opening-editor-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
});

test("readers cannot prepare and company replacement clears private history", async ({ page }) => {
  await installTaxOpeningsApi(page, ["payroll.read"]);
  await page.goto(detail);
  await expect(page.getByRole("link", { name: "Edit opening", exact: true })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Verify opening", exact: true })).toHaveCount(0);
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
  await expect(page.getByRole("table", { name: "Opening tax history", exact: true })).toContainText(
    "South Tax Employee",
  );
  await expect(page.getByText("Prepare opening revision 2", { exact: true })).toHaveCount(0);
});
