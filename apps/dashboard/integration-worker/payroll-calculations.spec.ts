import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "../integration/fixtures/admin-company";
import { signInAndEnrollAuthenticator } from "../integration/fixtures/sign-in-and-enroll-authenticator";
import { payrollCalculationMinor } from "../src/features/payroll/data/mappers/payroll-run-employee-mapper";
import {
  payrollRunDetailsDto,
  payrollRunEmployeeDto,
} from "../src/features/payroll/data/models/payroll-calculation-dto";
import type { PayrollInputReviewDto } from "../src/features/payroll/data/models/payroll-input-dto";
import type { WorkPeriodOverviewDto } from "../src/features/workforce/data/models/work-period-overview-dto";
import { compensationTerms } from "../tests/fixtures/compensations";
import { exercisePayrollFinalization } from "./fixtures/payroll-finalization-workflow";
import { exercisePayrollReview } from "./fixtures/payroll-review-workflow";

test("calculation, publication and payment retain independently reviewed evidence and recover original commands", async ({
  page,
  context,
  browser,
  baseURL,
}) => {
  test.setTimeout(180_000);
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-calculation-admin@example.invalid",
    "CALCULATION",
    "Payroll Calculation Company",
  );
  const base = `/api/v1/companies/${company}`,
    employee = randomUUID(),
    period = randomUUID(),
    preparerId = "a0000000-0000-4000-8000-000000000051",
    reviewerId = "a0000000-0000-4000-8000-000000000052",
    adminHeaders = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  for (const [account, permissions] of [
    [
      preparerId,
      ["company.read", "payroll.calculate", "payroll.policy.manage", "payroll.compensation.manage"],
    ],
    [reviewerId, ["company.read", "payroll.review"]],
  ] as const) {
    const response = await context.request.put(`${base}/members/${account}`, {
      headers: adminHeaders(),
      data: { active: true, permissions, reason: "Independent calculation fixture roles" },
    });
    expect(response.status()).toBe(200);
  }
  const employment = await context.request.post(`${base}/employees`, {
    headers: adminHeaders(),
    data: {
      id: employee,
      employeeNumber: "CALC-001",
      person: { id: randomUUID(), legalName: "Calculation Employee", nationality: "ID" },
      terms: {
        effectiveFrom: "2025-01-01",
        startDate: "2025-01-01",
        status: "ACTIVE",
        contract: "PERMANENT",
      },
      reason: "Payroll calculation acceptance",
    },
  });
  expect(employment.status()).toBe(200);
  const calendar = await context.request.get(`${base}/workforce/periods/2026-01`);
  expect(calendar.status()).toBe(200);
  const companyDate = ((await calendar.json()) as WorkPeriodOverviewDto).companyDate,
    lastDate = new Date(Date.parse(`${companyDate.slice(0, 7)}-01T00:00:00Z`) - 86_400_000)
      .toISOString()
      .slice(0, 10),
    month = lastDate.slice(0, 7),
    year = month.slice(0, 4),
    throughMonth = Number(month.slice(5, 7)) - 1,
    input = `${base}/payroll/employees/${employee}/inputs/${month}`,
    opening = `${base}/payroll/employees/${employee}/tax-openings/${year}`,
    runs = `${base}/payroll/periods/${period}/runs`;
  const before = (await (
    await context.request.get(`${base}/workforce/periods/${month}`)
  ).json()) as WorkPeriodOverviewDto;
  const closing = await context.request.post(`${base}/workforce/periods/${month}/close`, {
    headers: adminHeaders(),
    data: {
      expectedVersion: before.period?.version ?? 0,
      reason: "Close fictional attendance for calculation",
    },
  });
  expect(closing.status()).toBe(200);
  await expect
    .poll(
      async () => {
        const response = await context.request.get(`${base}/workforce/periods/${month}`);
        expect(response.status()).toBe(200);
        return ((await response.json()) as WorkPeriodOverviewDto).period?.status;
      },
      { timeout: 30_000, intervals: [250, 500, 1000] },
    )
    .toBe("CLOSED");
  if (!baseURL) throw new Error("Integration base URL required");
  const preparer = await browser.newContext({ baseURL }),
    reviewer = await browser.newContext({ baseURL }),
    finalReviewer = await browser.newContext({ baseURL });
  try {
    const editor = await preparer.newPage(),
      checker = await reviewer.newPage();
    await signInAndEnrollAuthenticator(editor, "browser-calculation-preparer@example.invalid");
    await editor.getByRole("combobox", { name: "Company", exact: true }).selectOption(company);
    await signInAndEnrollAuthenticator(checker, "browser-calculation-reviewer@example.invalid");
    await checker.getByRole("combobox", { name: "Company", exact: true }).selectOption(company);
    const operatorCsrf = (await (await preparer.request.get("/api/v1/auth/csrf")).json()) as {
      headerName: string;
      token: string;
    };
    const checkerCsrf = (await (await reviewer.request.get("/api/v1/auth/csrf")).json()) as {
      headerName: string;
      token: string;
    };
    const operatorHeaders = () => ({
      [operatorCsrf.headerName]: operatorCsrf.token,
      "Idempotency-Key": randomUUID(),
    });
    const checkerHeaders = () => ({
      [checkerCsrf.headerName]: checkerCsrf.token,
      "Idempotency-Key": randomUUID(),
    });
    const policy = await preparer.request.put(`${base}/payroll/policy`, {
      headers: operatorHeaders(),
      data: {
        expectedVersion: null,
        effectiveFrom: `${year}-01`,
        effectiveUntil: `${year}-12`,
        incomeTaxRuleId: "ID-PP58-PMK168-2024-v1",
        insuranceRuleId: "ID-BPJS-PU-v1",
        minimumMonthlyWage: "4000000",
        healthWageCap: "12000000",
        pensionWageCap: "10000000",
        contributionRounding: "HALF_UP",
        reviewReferences: ["https://example.test/fictional-policy"],
        reason: "Reviewed fictional company payroll policy",
      },
    });
    expect(policy.status()).toBe(200);
    const created = await preparer.request.post(`${base}/payroll/periods`, {
      headers: operatorHeaders(),
      data: {
        id: period,
        earningsMonth: month,
        plannedPaymentDate: `${companyDate.slice(0, 7)}-02`,
        employeeIds: [employee],
        reason: "Prepare fictional monthly payroll",
      },
    });
    expect(created.status()).toBe(200);
    expect(
      (await context.request.get(`${base}/payroll/periods/${period}/calculation-review`)).status(),
    ).toBe(403);
    await editor.goto(`/payroll/periods/${period}/calculate?company=${company}`);
    await editor.getByLabel("Income due date", { exact: true }).fill(lastDate);
    await editor
      .getByLabel("Review reference", { exact: true })
      .fill("Reviewed salary liabilities");
    await editor.getByLabel("Reason", { exact: true }).fill("Calculate the retained roster");
    const writes: { operation: string; body: Record<string, unknown> }[] = [];
    let dropStart = true;
    await editor.route(`**${runs}`, async (route) => {
      if (route.request().method() !== "POST") return route.continue();
      writes.push({
        operation: route.request().headers()["idempotency-key"] ?? "",
        body: route.request().postDataJSON() as Record<string, unknown>,
      });
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      if (dropStart) {
        dropStart = false;
        return route.abort("failed");
      }
      return route.fulfill({ response });
    });
    await editor.getByRole("button", { name: "Start calculation", exact: true }).click();
    await expect(editor.getByRole("status")).toContainText("The result is unconfirmed.");
    await editor.getByRole("button", { name: "Retry request", exact: true }).click();
    await expect(editor.getByRole("status")).toContainText("Calculation queued.");
    expect(writes).toHaveLength(2);
    expect(writes[1]).toEqual(writes[0]);
    const firstRun = String(writes[0]?.body.id);
    await editor.getByRole("link", { name: "View run", exact: true }).click();
    const results = editor.getByRole("table", { name: "Employee results", exact: true });
    await expect(results).toContainText("Prepare effective compensation before recalculating", {
      timeout: 30_000,
    });
    const failed = payrollRunDetailsDto.parse(
      await (await preparer.request.get(`${base}/payroll/runs/${firstRun}?limit=50`)).json(),
    );
    expect(failed.run).toMatchObject({ status: "CALCULATED", succeeded: 0, failed: 1 });
    expect(failed.job.status).toBe("SUCCEEDED");
    expect(failed.results.items[0]).toMatchObject({
      failure: { code: "payroll_compensation_required" },
      taxableGross: null,
      withheld: null,
      takeHome: null,
    });
    await editor
      .getByRole("button", { name: "View result: Calculation Employee", exact: true })
      .click();
    await expect(editor.getByRole("alert")).toContainText("Prepare effective compensation");
    await expect(editor.getByRole("region", { name: "Income tax", exact: true })).toHaveCount(0);
    await editor.getByRole("link", { name: "View run", exact: true }).click();
    await editor.getByRole("link", { name: "Abandon run", exact: true }).click();
    await editor
      .getByLabel("Reason", { exact: true })
      .fill("Complete and verify the missing sources");
    await editor.getByRole("button", { name: "Abandon run", exact: true }).click();
    await expect(editor.getByRole("status")).toContainText("Run abandoned.");

    const terms = compensationTerms();
    const compensation = await preparer.request.put(
      `${base}/payroll/employees/${employee}/compensation`,
      {
        headers: operatorHeaders(),
        data: {
          expectedVersion: null,
          expectedEmploymentVersion: 0,
          effectiveFrom: month,
          terms: { ...terms, tax: { ...terms.tax, verifiedOn: `${month}-01` } },
          reason: "Reviewed fictional compensation",
        },
      },
    );
    expect(compensation.status()).toBe(200);
    const reviewed = await preparer.request.get(`${input}/review`);
    expect(reviewed.status()).toBe(200);
    const inputReview = (await reviewed.json()) as PayrollInputReviewDto;
    if (!inputReview.source?.jobId) throw new Error("Closed attendance evidence required");
    const prepared = await preparer.request.put(input, {
      headers: operatorHeaders(),
      data: {
        expectedVersion: null,
        expectedEmploymentVersion: inputReview.employee.employmentVersion,
        workJobId: inputReview.source.jobId,
        expectedWorkPeriodVersion: inputReview.source.version,
        terms: {
          earnings: [],
          deductions: [],
          nonCashTaxable: "0",
          scheduledMonthUnits: null,
          dayResolutions: Array.from({ length: Number(lastDate.slice(8, 10)) }, (_, day) => ({
            workDate: `${month}-${String(day + 1).padStart(2, "0")}`,
            portion: "FULL",
            disposition: "PAID",
            reference: "Reviewed fictional unassigned calendar day",
          })),
          holidayAllowance: null,
          reviewReference: "Reviewed closed attendance with explicit pay decisions",
        },
        reason: "Declare reviewed monthly earnings and work decisions",
      },
    });
    expect(prepared.status()).toBe(200);
    const verifiedInput = await reviewer.request.post(`${input}/verify`, {
      headers: checkerHeaders(),
      data: { expectedVersion: 0, reason: "Independent review of the monthly source" },
    });
    expect(verifiedInput.status()).toBe(200);
    const savedOpening = await preparer.request.put(opening, {
      headers: operatorHeaders(),
      data: {
        expectedVersion: null,
        expectedEmploymentVersion: 0,
        terms: {
          throughMonth,
          residency: "RESIDENT",
          ptkp: "TK0",
          reference: "Fictional reviewed previous payroll records",
          history: {
            taxableGross: String(10_000_000 * throughMonth),
            retirementContributions: String(100_000 * throughMonth),
            qualifiedDonations: "0",
            withheld: String(250_000 * throughMonth),
            employmentMonths: throughMonth,
            previousEmployerNet: "0",
            previousEmployerWithheld: "0",
          },
        },
        reason: "Declare reviewed opening tax history",
      },
    });
    expect(savedOpening.status()).toBe(200);
    const verifiedOpening = await reviewer.request.post(`${opening}/verify`, {
      headers: checkerHeaders(),
      data: { expectedVersion: 0, reason: "Independent review of historical evidence" },
    });
    expect(verifiedOpening.status()).toBe(200);
    await editor.goto(`/payroll/periods/${period}/calculate?company=${company}`);
    await editor.getByLabel("Income due date", { exact: true }).fill(lastDate);
    await editor.getByLabel("Review reference", { exact: true }).fill("Verified revised sources");
    await editor.getByLabel("Reason", { exact: true }).fill("Calculate corrected payroll");
    await editor.getByRole("button", { name: "Start calculation", exact: true }).click();
    await expect(editor.getByRole("status")).toContainText("Calculation queued.");
    expect(writes).toHaveLength(3);
    const secondRun = String(writes[2]?.body.id);
    expect(secondRun).not.toBe(firstRun);
    await editor.getByRole("link", { name: "View run", exact: true }).click();
    await expect(results).toContainText("Calculation Employee", { timeout: 30_000 });
    const succeeded = payrollRunDetailsDto.parse(
      await (await preparer.request.get(`${base}/payroll/runs/${secondRun}?limit=50`)).json(),
    );
    expect(succeeded.run).toMatchObject({ status: "CALCULATED", succeeded: 1, failed: 0 });
    expect(succeeded.results.items[0]?.failure).toBeNull();
    await editor
      .getByRole("button", { name: "View result: Calculation Employee", exact: true })
      .click();
    await expect(editor.getByRole("region", { name: "Income tax", exact: true })).toBeVisible();
    await expect(editor.getByRole("table", { name: "Earnings", exact: true })).toContainText(
      "Meal allowance",
    );
    const retained = payrollRunEmployeeDto.parse(
      await (
        await preparer.request.get(`${base}/payroll/runs/${secondRun}/employees/${employee}`)
      ).json(),
    );
    if (!retained.calculation) throw new Error("Expected retained calculation output");
    for (const [summary, calculated] of [
      [retained.item.taxableGross, retained.calculation.tax.taxableGross],
      [retained.item.withheld, retained.calculation.tax.withheld],
      [retained.item.takeHome, retained.calculation.tax.takeHome],
    ] as const) {
      if (summary === null) throw new Error("Expected monetary result");
      expect(payrollCalculationMinor(summary)).toBe(payrollCalculationMinor(calculated));
    }
    await editor.screenshot({
      path: "../../.work/dashboard-payroll-calculation-real-output.png",
      fullPage: true,
    });
    await editor.getByRole("tab", { name: "Source evidence", exact: true }).click();
    await expect(
      editor.getByRole("table", { name: "Attendance evidence", exact: true }).getByRole("row"),
    ).toHaveCount(Number(lastDate.slice(8, 10)) + 1);
    const historical = payrollRunDetailsDto.parse(
      await (await reviewer.request.get(`${base}/payroll/runs/${firstRun}?limit=50`)).json(),
    );
    expect(historical.run.status).toBe("ABANDONED");
    expect(historical.results).toEqual(failed.results);
    expect((await (await preparer.request.get(runs)).json()).items).toHaveLength(2);
    const second = await exercisePayrollReview({
      finalReviewer,
      admin: context,
      preparer,
      reviewer,
      editor,
      checker,
      company,
      run: secondRun,
      month,
      firstReviewer: reviewerId,
      headers: adminHeaders,
    });
    await exercisePayrollFinalization({
      browser,
      baseURL,
      admin: context,
      preparer,
      finalReviewer,
      editor,
      checker,
      second,
      company,
      rejectedRun: secondRun,
      period,
      employee,
      month,
      incomeDueDate: lastDate,
      headers: adminHeaders,
    });
    await checker.goto(`/payroll/runs/${secondRun}?company=${company}`);
    await expect(
      checker.getByRole("table", { name: "Employee results", exact: true }),
    ).toBeVisible();
    await expect(checker.getByRole("link", { name: "Abandon run", exact: true })).toHaveCount(0);
    const revoked = await context.request.put(`${base}/members/${reviewerId}`, {
      headers: adminHeaders(),
      data: {
        active: true,
        permissions: ["company.read"],
        expectedVersion: 0,
        reason: "Withdraw payroll access",
      },
    });
    expect(revoked.status()).toBe(200);
    await checker.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(checker.getByRole("alert")).toBeVisible();
    await expect(checker.getByRole("table", { name: "Employee results", exact: true })).toHaveCount(
      0,
    );
  } finally {
    await preparer.close();
    await reviewer.close();
    await finalReviewer.close();
  }
});
