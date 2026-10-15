import type {
  PayrollCalculationReviewDto,
  PayrollRunDetailsDto,
  PayrollRunDto,
  PayrollRunEmployeeDto,
  PayrollRunItemDto,
} from "../../src/features/payroll/data/models/payroll-calculation-dto";
import { periodId, periodRecord } from "./payroll-periods";

export { periodId as calculationPeriod };
export const calculationCompany = "10000000-0000-4000-8000-000000000001",
  calculationActor = "20000000-0000-4000-8000-000000000001",
  calculationId = "ec000000-0000-4000-8000-000000000001",
  calculationJob = "ec100000-0000-4000-8000-000000000001",
  calculationWork = "ec200000-0000-4000-8000-000000000001";
export const calculationEmployeeId = (index = 1) =>
  `ec300000-0000-4000-8000-${String(index).padStart(12, "0")}`;
export function calculationReview(): PayrollCalculationReviewDto {
  return {
    period: periodRecord({ participantCount: 1 }),
    source: {
      id: "ec400000-0000-4000-8000-000000000001",
      month: "2026-09",
      version: 2,
      closed: true,
      jobId: calculationWork,
      timezone: "Asia/Jakarta",
    },
    policy: {
      version: 0,
      appliedRevision: 0,
      effectiveFrom: "2026-01",
      effectiveUntil: "2026-12",
      incomeTaxRuleId: "ID-PP58-PMK168-2024-v1",
      insuranceRuleId: "ID-BPJS-PU-v1",
    },
    runCount: 0,
    blockingIssues: [],
    availableActions: ["START"],
  };
}
export function calculationRun(changes: Partial<PayrollRunDto> = {}): PayrollRunDto {
  return {
    id: calculationId,
    periodId,
    number: 1,
    earningsMonth: "2026-09",
    taxMonth: "2026-09",
    incomeDueDate: "2026-09-30",
    plannedPaymentDate: "2026-10-02",
    timezone: "Asia/Jakarta",
    workJobId: calculationWork,
    workPeriodVersion: 2,
    policyRevision: 0,
    totalEmployees: 1,
    actorId: calculationActor,
    reviewReference: "Reviewed September liabilities",
    reason: "Calculate September payroll",
    createdAt: "2026-10-01T00:00:00Z",
    jobId: calculationJob,
    status: "CALCULATED",
    version: 1,
    processed: 1,
    succeeded: 1,
    failed: 0,
    finalizationId: null,
    ...changes,
  };
}
export function calculationItem(index = 1, code: string | null = null): PayrollRunItemDto {
  return {
    target: {
      ordinal: index,
      employeeId: calculationEmployeeId(index),
      employeeNumber: `CALC-${String(index).padStart(3, "0")}`,
      employeeName: `Calculation Employee ${String(index).padStart(3, "0")}`,
      employmentVersion: 0,
      compensationRevision: 0,
      inputId: `ec500000-0000-4000-8000-${String(index).padStart(12, "0")}`,
      inputRevision: 1,
      taxOpeningId: `ec600000-0000-4000-8000-${String(index).padStart(12, "0")}`,
      taxOpeningRevision: 1,
      previousAssessmentId: null,
      previousAssessmentRevision: 0,
    },
    jobId: calculationJob,
    completedAt: "2026-10-01T00:01:00Z",
    failure: code ? { kind: "CONFLICT", code, fields: {}, parameters: {} } : null,
    taxableGross: code ? null : "10000000.25",
    withheld: code ? null : "-0.50",
    takeHome: code ? null : "10000000.75",
  };
}
export function calculationDetails(
  run = calculationRun(),
  after = 0,
  limit = 50,
): PayrollRunDetailsDto {
  const status =
    run.status === "ABANDONED"
      ? "DRAFT"
      : run.status === "FINALIZED"
        ? "FINALIZED"
        : run.status === "CALCULATED"
          ? "CALCULATED"
          : "PROCESSING";
  const rows = Array.from({ length: run.processed }, (_, i) =>
    calculationItem(i + 1, i < run.failed ? "payroll_input_verification_required" : null),
  ).filter((row) => row.target.ordinal > after);
  return {
    run,
    period: periodRecord({
      participantCount: run.totalEmployees,
      status,
      version: run.status === "ABANDONED" ? 3 : status === "CALCULATED" ? 2 : 1,
      currentRunId: status === "DRAFT" ? null : run.id,
    }),
    attempts: [
      {
        jobId: run.jobId,
        number: 1,
        baseCompleted: 0,
        startedAt: run.createdAt,
        reason: run.reason,
      },
    ],
    job: {
      jobId: run.jobId,
      status:
        run.status === "PROCESSING"
          ? "RUNNING"
          : run.status === "STOPPED"
            ? "CANCELLED"
            : "SUCCEEDED",
      completedItems:
        run.processed + (run.status === "CALCULATED" || run.status === "FINALIZED" ? 1 : 0),
      totalItems: run.totalEmployees + 1,
      attempts: 1,
      version: 3,
      cancellationRequested: run.status === "STOPPED",
      failureCode: null,
    },
    results: {
      items: rows.slice(0, limit),
      nextCursor: rows.length > limit ? String(rows[limit - 1]?.target.ordinal) : null,
    },
    availableActions:
      run.status === "PROCESSING"
        ? ["CANCEL_JOB"]
        : run.status === "STOPPED"
          ? ["RESUME", "ABANDON"]
          : run.status === "CALCULATED"
            ? ["ABANDON"]
            : [],
  };
}
export function calculationEmployee(code: string | null = null): PayrollRunEmployeeDto {
  const days = Array.from({ length: 30 }, (_, i) => `2026-09-${String(i + 1).padStart(2, "0")}`),
    policy = calculationReview().policy;
  if (!policy) throw new Error("Fixture policy required");
  return {
    item: calculationItem(1, code),
    facts: code
      ? null
      : {
          month: "2026-09",
          incomeDueDate: "2026-09-30",
          plannedPaymentDate: "2026-10-02",
          policy,
          compensation: { basicSalary: "10000000.25" },
          input: { reviewReference: "Reviewed attendance and earnings" },
          taxHistory: { throughMonth: 8, reference: "Reviewed previous employer records" },
          workDays: days.map((date) => ({
            date,
            schedule: "OFF",
            attendance: "OFF",
            officialHoliday: false,
          })),
          leaveDays: [],
        },
    calculation: code
      ? null
      : {
          units: { total: "30", payable: "30", unpaid: "0", employedDates: days },
          earnings: [
            { kind: "BASIC", code: "BASIC", name: null, amount: "10000000.25", taxable: true },
          ],
          overtime: [],
          holiday: null,
          contributions: [],
          tax: {
            ruleId: policy.incomeTaxRuleId,
            taxableGross: "10000000.25",
            taxAllowance: "0",
            deductionAllowance: "0",
            withheld: "-0.50",
            takeHome: "10000000.75",
            category: null,
            effectiveRate: null,
            annualNet: "100000000.25",
            annualizedNet: null,
            annualTaxable: "46000000",
            annualTax: "2300000",
          },
          employeeDeductions: "-0.50",
          employerCost: "10000000.25",
        },
  };
}
export const calculationFields = {
  incomeDueDate: "2026-09-30",
  reviewReference: "Reviewed September liabilities",
  reason: "Calculate September payroll",
};
export const calculationStart = () => ({
  ...calculationFields,
  id: calculationId,
  expectedPeriodVersion: 0,
  expectedWorkPeriodVersion: 2,
  expectedPolicyVersion: 0,
});
