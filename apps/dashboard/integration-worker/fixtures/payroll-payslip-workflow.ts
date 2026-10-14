import { readFile } from "node:fs/promises";
import { type BrowserContext, expect, type Page } from "@playwright/test";
import {
  type PayrollPayslipSummaryDto,
  payrollPayslipDto,
} from "../../src/features/payroll/data/models/payroll-payslip-dto";

/** Borrow the caller's read context; publication and all account lifetimes remain caller-owned. */
export async function exercisePayrollPayslip(input: {
  reader: Page;
  admin: BrowserContext;
  company: string;
  month: string;
  summary: PayrollPayslipSummaryDto;
  headers: () => Record<string, string>;
}) {
  const { reader, admin, company, month, summary, headers } = input;
  const base = `/api/v1/companies/${company}/payroll/payslips`;
  const reads: string[] = [];
  const observed = (request: import("@playwright/test").Request) => {
    const url = new URL(request.url());
    if (url.pathname.startsWith(base)) reads.push(url.pathname);
  };
  reader.on("request", observed);
  try {
    await reader.goto(
      `/payroll/payslips?company=${company}&from=${month}&until=${month}&scope=AUTHORIZED`,
    );
    const table = reader.getByRole("table", { name: "Payslips", exact: true });
    await expect(table.getByRole("row")).toHaveCount(2);
    await expect(table).toContainText(summary.employeeNumber);
    await reader
      .getByLabel("Employee name or number", { exact: true })
      .fill(summary.employeeNumber);
    await reader.getByRole("button", { name: "Apply filters", exact: true }).click();
    await expect(reader).toHaveURL(
      new RegExp(`query=${encodeURIComponent(summary.employeeNumber)}`, "u"),
    );
    await expect(table).toContainText(summary.employeeName);
    await reader
      .getByRole("button", {
        name: `View payslip: ${summary.employeeName} · ${month}`,
        exact: true,
      })
      .click();
    await expect(reader.getByRole("region", { name: "Overview", exact: true })).toContainText(
      summary.employeeName,
    );
    const detailsResponse = await reader.request.get(`${base}/${summary.id}?scope=AUTHORIZED`);
    expect(detailsResponse.status()).toBe(200);
    const details = payrollPayslipDto.parse(await detailsResponse.json());
    expect(details.summary).toEqual(summary);
    const detailReads = reads.filter((path) => path === `${base}/${summary.id}`).length;
    await reader.getByRole("tab", { name: "Payment history", exact: true }).click();
    await expect(reader.getByRole("status")).toContainText("No payment attempts recorded.");
    await reader.getByRole("button", { name: "Refresh payments", exact: true }).click();
    await expect(reader.getByRole("status")).toContainText("No payment attempts recorded.");
    expect(reads.filter((path) => path === `${base}/${summary.id}`)).toHaveLength(detailReads);
    for (const language of ["EN", "ID"]) {
      await reader.getByLabel("PDF language", { exact: true }).selectOption(language);
      const completed = reader.waitForEvent("download");
      await reader.getByRole("button", { name: "Download PDF", exact: true }).click();
      const download = await completed;
      expect(download.suggestedFilename()).toBe(
        `payslip-${summary.id}-${language.toLowerCase()}.pdf`,
      );
      const path = await download.path();
      if (!path) throw new Error("Published PDF download required");
      const content = await readFile(path);
      expect(content.subarray(0, 5).toString()).toBe("%PDF-");
      expect(content.length).toBeLessThanOrEqual(1024 * 1024);
      await expect(
        reader.getByRole("status").filter({ hasText: "PDF sent to your browser's downloads." }),
      ).toBeVisible();
    }
    expect(reads.filter((path) => path.endsWith("/pdf"))).toHaveLength(2);
    await reader.getByRole("tab", { name: "Calculation", exact: true }).click();
    await reader.screenshot({
      path: "../../.work/dashboard-payslip-real-calculation.png",
      fullPage: true,
      animations: "disabled",
    });
    await reader.getByRole("link", { name: "All payslips", exact: true }).click();
    await expect(reader.getByLabel("Employee name or number", { exact: true })).toHaveValue(
      summary.employeeNumber,
    );
    await reader.getByLabel("Scope", { exact: true }).selectOption("SELF");
    await reader.getByRole("button", { name: "Apply filters", exact: true }).click();
    await expect(reader.getByRole("status")).toContainText("No published payslips");
    await reader.getByLabel("Scope", { exact: true }).selectOption("AUTHORIZED");
    await reader.getByRole("button", { name: "Apply filters", exact: true }).click();
    await reader
      .getByRole("button", {
        name: `View payslip: ${summary.employeeName} · ${month}`,
        exact: true,
      })
      .click();
    await expect(reader.getByRole("button", { name: "Download PDF", exact: true })).toBeEnabled();
    const revoked = await admin.request.put(
      `/api/v1/companies/${company}/members/a0000000-0000-4000-8000-000000000053`,
      {
        headers: headers(),
        data: {
          active: true,
          expectedVersion: 1,
          permissions: ["company.read"],
          reason: "Withdraw published payslip read authority",
        },
      },
    );
    expect(revoked.status()).toBe(200);
    await reader.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(reader.getByRole("alert")).toBeVisible();
    await expect(reader.getByRole("region", { name: "Overview", exact: true })).toHaveCount(0);
    await expect(reader.getByRole("button", { name: "Download PDF", exact: true })).toHaveCount(0);
  } finally {
    reader.off("request", observed);
  }
}
