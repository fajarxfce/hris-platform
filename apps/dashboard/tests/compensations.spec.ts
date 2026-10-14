import { expect, type Page, test } from "@playwright/test";
import { compensationKey, installCompensationsApi } from "./compensations-api";
import { compensationEmployee } from "./fixtures/compensations";
import { fillCompensationForm } from "./fixtures/fill-compensation-form";
import { companyIds } from "./identity-api";

const query = `company=${companyIds[0]}&month=2026-06`,
  directory = `/payroll/compensation?company=${companyIds[0]}`,
  detail = `/payroll/compensation/${compensationEmployee}?${query}`,
  editor = `/payroll/compensation/${compensationEmployee}/edit?${query}`;
async function openEditor(page: Page) {
  await page.goto(editor);
  await expect(page.getByLabel("Basic salary (IDR)", { exact: true })).toHaveValue("12000000.75");
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed current compensation");
}
test("employee discovery includes unconfigured records, literal search and bounded navigation", async ({
  page,
}) => {
  const api = await installCompensationsApi(page);
  await page.goto(directory);
  const table = page.getByRole("table", { name: "Employees", exact: true });
  await expect(table.getByRole("row")).toHaveCount(21);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(4);
  await expect(table).toContainText("Upcoming Employee 23");
  await page.getByLabel("Search name or employee number", { exact: true }).fill("Rani");
  await page.getByRole("button", { name: "Search", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(2);
  await expect(page).toHaveURL(`${directory}&query=Rani`);
  await page.getByRole("button", { name: "View compensation: Rani Pratama", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Rani Pratama", exact: true })).toBeVisible();
  expect(api.reads.some((url) => url.searchParams.get("after") === "EMP-020")).toBe(true);
});
test("monthly evidence remains independent of the current editable revision", async ({ page }) => {
  const api = await installCompensationsApi(page);
  await page.goto(detail);
  await expect(
    page.getByRole("region", { name: "Effective compensation", exact: true }),
  ).toContainText("10,000,000.25");
  const table = page.getByRole("table", { name: "Compensation history", exact: true });
  await expect(table.getByRole("row")).toHaveCount(21);
  const reads = api.reads.length;
  await page.getByRole("button", { name: "View: Revision 0", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText("Compensation review 0");
  expect(api.reads).toHaveLength(reads);
  await page.getByRole("button", { name: "Close details", exact: true }).click();
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(5);
  await page.getByLabel("Payroll month", { exact: true }).fill("2025-01");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("No compensation applies to this month.");
  await expect(table).toBeVisible();
  await page.getByRole("button", { name: "View latest revision", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText("12,000,000.75");
});
test("initial setup requires explicit registration and saves complete nested terms", async ({
  page,
}) => {
  const api = await installCompensationsApi(page);
  const employee = "ca000000-0000-4000-8000-000000000023";
  await page.goto(`/payroll/compensation/${employee}?${query}`);
  await expect(page.getByRole("status")).toContainText("No compensation configured.");
  await page.getByRole("link", { name: "Set compensation", exact: true }).click();
  await expect(page.getByLabel("Basic salary (IDR)", { exact: true })).toHaveValue("");
  await expect(page.getByRole("combobox", { name: "Tax residency", exact: true })).toHaveValue("");
  await fillCompensationForm(page);
  await page.getByRole("button", { name: "Save compensation", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Compensation saved.");
  expect(api.writes[0]?.body).toMatchObject({
    expectedVersion: null,
    expectedEmploymentVersion: 7,
    terms: {
      basicSalary: "10000000.25",
      fixedEarnings: [{ code: "MEAL", amount: "500000.75", taxable: true }],
      tax: { residency: "RESIDENT", residenceCountry: "ID" },
      payBasis: { workWeek: "SIX_DAYS", shortestWorkDay: "SATURDAY" },
    },
  });
});
test("invalid decimal, risk and exemption fields never issue a command", async ({ page }) => {
  const api = await installCompensationsApi(page);
  await openEditor(page);
  await page.getByLabel("Basic salary (IDR)", { exact: true }).fill("1.001");
  await page.getByRole("checkbox", { name: "Pension (JP)", exact: true }).uncheck();
  await page.getByRole("button", { name: "Save compensation", exact: true }).click();
  await expect(page.getByLabel("Basic salary (IDR)", { exact: true })).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  await expect(page.getByLabel("Exemption reason", { exact: true })).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  expect(api.writes).toHaveLength(0);
});
test("response loss keeps the operation through a rejected retry", async ({ page }) => {
  const api = await installCompensationsApi(page);
  await openEditor(page);
  api.dropNext();
  await page.getByRole("button", { name: "Save compensation", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save result is unconfirmed");
  await expect(page.getByLabel("Reason", { exact: true })).toHaveAttribute("readonly", "");
  api.failNext("stale_employment_version");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("button", { name: "Retry save", exact: true })).toBeEnabled();
  await expect(
    page.getByRole("button", { name: "Load current compensation", exact: true }),
  ).toHaveCount(0);
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Compensation saved.");
  expect(api.writes).toHaveLength(3);
  for (const write of api.writes) expect(write).toEqual(api.writes[0]);
});
test("employment changes require protected reload with the new independent version", async ({
  page,
}) => {
  const api = await installCompensationsApi(page);
  await openEditor(page);
  api.advanceEmployment();
  await page.getByRole("button", { name: "Save compensation", exact: true }).click();
  await page.getByRole("button", { name: "Load current compensation", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed changed employment");
  await page.getByRole("button", { name: "Save compensation", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Compensation saved.");
  expect(
    api.writes.map((write) => [write.body.expectedVersion, write.body.expectedEmploymentVersion]),
  ).toEqual([
    [23, 7],
    [23, 8],
  ]);
  expect(api.writes[0]?.operation).not.toBe(api.writes[1]?.operation);
});
test("read permission exposes no salary editor", async ({ page }) => {
  const api = await installCompensationsApi(page, ["payroll.read"]);
  await page.goto(detail);
  await expect(
    page.getByRole("region", { name: "Effective compensation", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("link", { name: "New revision", exact: true })).toHaveCount(0);
  const reads = api.reads.length;
  await page.goto(editor);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.reads).toHaveLength(reads);
  expect(api.writes).toHaveLength(0);
});
test("company replacement discards the old employee route and late private results", async ({
  page,
}) => {
  const api = await installCompensationsApi(page),
    held = api.holdRead();
  try {
    await page.goto(editor);
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page).toHaveURL(`/payroll/compensation?company=${companyIds[1]}`);
    await expect(page.getByRole("table", { name: "Employees", exact: true })).toContainText(
      "Bima Santoso",
    );
    held.release();
    await expect(page.getByRole("main")).not.toContainText("Rani Pratama");
    expect(api.writes).toHaveLength(0);
  } finally {
    held.release();
  }
});
test("locale and theme retain the nested draft on a narrow screen", async ({ page }) => {
  const api = await installCompensationsApi(page);
  await openEditor(page);
  await page.getByLabel("Basic salary (IDR)", { exact: true }).fill("12000000.99");
  const reads = api.reads.length;
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByLabel("Gaji pokok (IDR)", { exact: true })).toHaveValue("12000000.99");
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue(
    "Reviewed current compensation",
  );
  expect(api.reads).toHaveLength(reads);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-compensation-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
});
test("the bound employee cannot open a compensation editor", async ({ page }) => {
  const api = await installCompensationsApi(page),
    record = api.records.get(compensationKey(companyIds[0], compensationEmployee));
  if (!record) throw new Error("Missing fixture");
  record.canChange = false;
  await page.goto(detail);
  await expect(
    page.getByRole("region", { name: "Effective compensation", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("link", { name: "New revision", exact: true })).toHaveCount(0);
  await page.goto(editor);
  await expect(page.getByRole("alert")).toContainText(
    "Compensation cannot be changed with this account.",
  );
  await expect(page.getByLabel("Basic salary (IDR)", { exact: true })).toHaveCount(0);
  expect(api.writes).toHaveLength(0);
});
