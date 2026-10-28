import { expect, type Page } from "@playwright/test";
import { formatIdrAmount } from "../../src/core/presentation/money/format-idr-amount";
import { payrollCalculationMinor } from "../../src/features/payroll/data/mappers/payroll-run-employee-mapper";
import {
  type PayrollPayslipSummaryDto,
  payrollPayslipDto,
} from "../../src/features/payroll/data/models/payroll-payslip-dto";
import { payrollCostReportDto } from "../../src/features/reporting/data/models/payroll-cost-report-dto";

export async function readPayrollCostReport(reader: Page, company: string, month: string) {
  const response = await reader.request.get(
    `/api/v1/companies/${company}/reports/payroll-costs?from=${month.slice(0, 4)}-01&until=${month}`,
  );
  expect(response.status()).toBe(200);
  expect(response.headers()["cache-control"]).toContain("no-store");
  return payrollCostReportDto.parse(await response.json());
}

/** Borrow the caller's independent reader and reconcile the report with the exact published statement. */
export async function exercisePayrollCostReport(
  reader: Page,
  company: string,
  summary: PayrollPayslipSummaryDto,
) {
  const month = summary.taxMonth,
    from = `${month.slice(0, 4)}-01`;
  const report = await readPayrollCostReport(reader, company, month);
  const response = await reader.request.get(
    `/api/v1/companies/${company}/payroll/payslips/${summary.id}?revision=${summary.version}`,
  );
  expect(response.status()).toBe(200);
  const slip = payrollPayslipDto.parse(await response.json());
  expect(slip.summary).toEqual(summary);
  expect(report.totals).toMatchObject({
    assessments: 1,
    employments: 1,
    revisedAssessments: summary.version > 0 ? 1 : 0,
  });
  for (const [key, value] of Object.entries({
    employerCost: slip.calculation.employerCost,
    taxableGross: slip.calculation.tax.taxableGross,
    employeeDeductions: slip.calculation.employeeDeductions,
    withheld: slip.calculation.tax.withheld,
    takeHome: slip.calculation.tax.takeHome,
  })) {
    const actual =
      report.totals[
        key as "employerCost" | "taxableGross" | "employeeDeductions" | "withheld" | "takeHome"
      ];
    expect(payrollCalculationMinor(actual)).toBe(payrollCalculationMinor(value));
  }
  expect(report.months.find((row) => row.month === month)?.counts).toEqual(report.totals);
  expect(
    report.months.filter((row) => row.month !== month).every((row) => row.counts.assessments === 0),
  ).toBe(true);
  await reader.goto(`/reports/payroll-costs?company=${company}&from=${from}&until=${month}`);
  await expect(
    reader.getByRole("region", { name: "Published payslips", exact: true }),
  ).toContainText("1");
  await expect(reader.getByRole("region", { name: "Revised payslips", exact: true })).toContainText(
    String(report.totals.revisedAssessments),
  );
  for (const [key, label] of [
    ["employerCost", "Employer cost"],
    ["taxableGross", "Taxable gross"],
    ["employeeDeductions", "Employee deductions"],
    ["withheld", "Tax withheld / refunded"],
    ["takeHome", "Take-home pay"],
  ] as const)
    await expect(reader.getByRole("region", { name: label, exact: true })).toContainText(
      formatIdrAmount(report.totals[key], "en"),
    );
  const table = reader.getByRole("table", { name: "By earnings month", exact: true });
  await expect(table.getByRole("row")).toHaveCount(report.months.length + 1);
  await table
    .getByRole("button", { name: /^View payslips:/u })
    .last()
    .click();
  await expect(reader.getByLabel("From month", { exact: true })).toHaveValue(month);
  await expect(reader.getByLabel("Through month", { exact: true })).toHaveValue(month);
  await reader
    .getByRole("button", { name: `View payslip: ${summary.employeeName} · ${month}`, exact: true })
    .click();
  await expect(reader.getByRole("region", { name: "Overview", exact: true })).toContainText(
    summary.employeeName,
  );
  await reader.goBack();
  await expect(reader.getByRole("table", { name: "Payslips", exact: true })).toContainText(
    summary.employeeName,
  );
  await reader.goBack();
  await expect(
    reader.getByRole("heading", { name: "Payroll cost report", exact: true }),
  ).toBeVisible();
  await expect(reader.getByLabel("From month", { exact: true })).toHaveValue(from);
  await expect(reader.getByLabel("Until month", { exact: true })).toHaveValue(month);
  await expect(reader.getByRole("region", { name: "Employer cost", exact: true })).toContainText(
    formatIdrAmount(report.totals.employerCost, "en"),
  );
  if (summary.version > 0)
    await reader.screenshot({
      path: "../../.work/dashboard-payroll-cost-real.png",
      fullPage: true,
      animations: "disabled",
    });
  return report;
}
