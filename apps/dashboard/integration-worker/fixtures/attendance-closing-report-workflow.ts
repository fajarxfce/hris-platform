import { type Browser, type BrowserContext, expect, type Page } from "@playwright/test";
import { signInAndEnrollAuthenticator } from "../../integration/fixtures/sign-in-and-enroll-authenticator";
import {
  attendanceClosingEmployeePageDto,
  attendanceClosingReportDto,
} from "../../src/features/reporting/data/models/attendance-closing-report-dto";
import { attendanceAdjustmentDetailsDto } from "../../src/features/workforce/data/models/attendance-adjustment-dto";
import type { WorkPeriodSnapshotDto } from "../../src/features/workforce/data/models/work-period-snapshot-dto";
export async function readClosingReport(reader: Page, company: string, month: string) {
  const response = await reader.request.get(
    `/api/v1/companies/${company}/reports/attendance-closings?from=${month.slice(0, 4)}-01&until=${month}`,
  );
  expect(response.status()).toBe(200);
  expect(response.headers()["cache-control"]).toContain("no-store");
  return attendanceClosingReportDto.parse(await response.json());
}
/** The caller retains its writer; a separate MFA-enrolled read-only account reconciles retained evidence. */
export async function exerciseClosingReport(input: {
  browser: Browser;
  baseURL: string | undefined;
  admin: BrowserContext;
  company: string;
  employee: string;
  month: string;
  original: WorkPeriodSnapshotDto;
  headers: () => Record<string, string>;
}) {
  const { browser, baseURL, admin, company, employee, month, original, headers } = input;
  const grant = await admin.request.put(
    `/api/v1/companies/${company}/members/a0000000-0000-4000-8000-000000000085`,
    {
      headers: headers(),
      data: {
        active: true,
        permissions: ["company.read", "reports.read", "workforce.read"],
        reason: "Independent historical attendance report reader",
      },
    },
  );
  expect(grant.status()).toBe(200);
  if (!baseURL) throw new Error("Missing fixture base URL");
  const context = await browser.newContext({ baseURL });
  try {
    const reader = await context.newPage();
    await signInAndEnrollAuthenticator(reader, "browser-attendance-closing-reader@example.invalid");
    await reader.getByRole("combobox", { name: "Company", exact: true }).selectOption(company);
    const report = await readClosingReport(reader, company, month);
    expect(report.totals).toMatchObject({
      closedMonths: 1,
      employmentMonths: 1,
      employments: 1,
      adjustedEmploymentMonths: 1,
      days: original.days.length,
    });
    const rowsResponse = await reader.request.get(
      `/api/v1/companies/${company}/reports/attendance-closings/employees?month=${month}&limit=50`,
    );
    expect(rowsResponse.status()).toBe(200);
    const rows = attendanceClosingEmployeePageDto.parse(await rowsResponse.json());
    expect(rows.items).toHaveLength(1);
    expect(rows.nextCursor).toBeNull();
    const row = rows.items[0];
    if (!row?.adjustment) throw new Error("Missing applied closing revision");
    expect(row.employeeId).toBe(employee);
    expect(row.counts).toEqual(report.totals);
    expect(row.adjustment.revision).toBe(2);
    const retained = await reader.request.get(
      `/api/v1/companies/${company}/workforce/employees/${employee}/attendance/adjustments/${row.adjustment.id}`,
    );
    expect(retained.status()).toBe(200);
    const current = attendanceAdjustmentDetailsDto.parse(await retained.json());
    expect(current.summary.revision).toBe(2);
    expect(current.snapshot.period.id).toBe(row.closing.periodId);
    for (const [key, snapshot] of [
      ["original", original],
      ["current", current.snapshot],
    ] as const) {
      for (const [name, fact] of [
        ["workedDays", "WORKED"],
        ["absenceDays", "ABSENCE_RECORDED"],
        ["unrecordedDays", "UNRECORDED"],
        ["offDays", "OFF"],
        ["unassignedDays", "UNASSIGNED"],
      ] as const)
        expect(report.totals[key][name]).toBe(snapshot.days.filter((d) => d.fact === fact).length);
      expect(report.totals[key].acceptedMinutes).toBe(
        snapshot.days.reduce((sum, d) => sum + d.acceptedMinutes, 0),
      );
    }
    expect(report.totals.current.absenceDays).toBe(report.totals.original.absenceDays + 1);
    expect(report.months.find((row) => row.month === month)?.counts).toEqual(report.totals);
    const from = `${month.slice(0, 4)}-01`;
    await reader.goto(
      `/reports/attendance-closings?company=${company}&from=${from}&until=${month}`,
    );
    await expect(
      reader.getByRole("region", { name: "Employment records", exact: true }),
    ).toContainText("1");
    const facts = reader.getByRole("table", { name: "Attendance facts", exact: true });
    await expect(facts.getByRole("row").filter({ hasText: "Recorded absence" })).toContainText(
      String(report.totals.current.absenceDays),
    );
    await reader.screenshot({
      path: "../../.work/attendance-closing-report-real.png",
      fullPage: true,
      animations: "disabled",
    });
    const period = reader.getByRole("table", { name: "Published months", exact: true });
    await period
      .getByRole("button", { name: /^View employees:/u })
      .last()
      .click();
    const employees = reader.getByRole("table", { name: "Closing employees", exact: true });
    await expect(employees.getByRole("row")).toHaveCount(2);
    await expect(employees).not.toContainText("Later Hire");
    await employees.getByRole("button", { name: /^View selected snapshot:/u }).click();
    await expect(reader).toHaveURL(new RegExp(`/adjustments/${row.adjustment.id}\\?`));
    await expect(
      reader.getByRole("table", { name: "Retained monthly attendance", exact: true }),
    ).toBeVisible();
    await reader.getByRole("link", { name: "Original closing", exact: true }).click();
    await expect(
      reader.getByRole("region", { name: "Recorded absence (days)", exact: true }),
    ).toContainText(String(report.totals.original.absenceDays));
    await reader.getByRole("link", { name: "Back to report", exact: true }).click();
    await expect(employees).toBeVisible();
    await reader.getByRole("link", { name: "Back to report", exact: true }).click();
    await expect(reader.getByLabel("From month", { exact: true })).toHaveValue(from);
    await expect(reader.getByLabel("Until month", { exact: true })).toHaveValue(month);
  } finally {
    await context.close();
  }
}
