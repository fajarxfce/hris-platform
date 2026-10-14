import type { Page } from "@playwright/test";
export async function fillCompensationForm(page: Page, verifiedOn = "2026-01-15") {
  await page.getByLabel("Basic salary (IDR)", { exact: true }).fill("10000000.25");
  await page.getByRole("button", { name: "Add earning", exact: true }).click();
  const earning = page.getByRole("group", { name: "Fixed earnings 1", exact: true });
  await earning.getByLabel("Code", { exact: true }).fill("MEAL");
  await earning.getByLabel("Name", { exact: true }).fill("Meal allowance");
  await earning.getByLabel("Amount (IDR)", { exact: true }).fill("500000.75");
  await page.getByRole("combobox", { name: "Tax treatment", exact: true }).selectOption("GROSS");
  await page.getByRole("combobox", { name: "Tax residency", exact: true }).selectOption("RESIDENT");
  await page.getByRole("combobox", { name: "PTKP status", exact: true }).selectOption("TK0");
  await page.getByLabel("Residence country (ISO code)", { exact: true }).fill("ID");
  await page.getByLabel("Verified on", { exact: true }).fill(verifiedOn);
  await page
    .getByLabel("Tax verification reference", { exact: true })
    .fill("Reviewed fictional tax registration");
  await page.getByLabel("Contribution wage (IDR)", { exact: true }).fill("10000000.25");
  await page
    .getByRole("combobox", { name: "Occupational risk category", exact: true })
    .selectOption("LOW");
  for (const name of [
    "Health (BPJS Kesehatan)",
    "Old-age savings (JHT)",
    "Pension (JP)",
    "Work accident (JKK)",
    "Death (JKM)",
  ])
    await page.getByRole("checkbox", { name, exact: true }).check();
  await page
    .getByRole("checkbox", { name: "Configure proration, overtime and THR", exact: true })
    .check();
  await page
    .getByRole("combobox", { name: "Proration basis", exact: true })
    .selectOption("CALENDAR_DAYS");
  await page.getByRole("combobox", { name: "Work week", exact: true }).selectOption("SIX_DAYS");
  await page
    .getByRole("combobox", { name: "Shortest workday", exact: true })
    .selectOption("SATURDAY");
  await page
    .getByRole("combobox", { name: "Overtime eligibility", exact: true })
    .selectOption("ELIGIBLE");
  await page
    .getByRole("combobox", { name: "THR service month convention", exact: true })
    .selectOption("COMPLETE_MONTHS");
  await page
    .getByRole("combobox", { name: "Earnings rounding", exact: true })
    .selectOption("HALF_UP");
  await page
    .getByLabel("Calculation basis review reference", { exact: true })
    .fill("Reviewed fictional calculation basis");
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed employee compensation");
}
