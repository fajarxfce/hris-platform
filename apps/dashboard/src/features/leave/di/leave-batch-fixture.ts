import type { AccountId, CompanyId, OperationId } from "../../../core/domain/identifiers";
import { toLeaveBatchDetails, toLeaveBatchReview } from "../data/mappers/leave-batch-mapper";
import type { LeaveBatchDetailsDto, LeaveBatchReviewDto } from "../data/models/leave-batch-dto";
import type { LeaveBatchId } from "../domain/entities/leave-batch";
export const batchCompany = "10000000-0000-4000-8000-000000000001" as CompanyId;
export const batchAccount = "20000000-0000-4000-8000-000000000001" as AccountId;
export const batchId = "40000000-abcd-4000-8000-000000000001" as LeaveBatchId;
export const batchJob = "60000000-0000-4000-8000-000000000001";
export const batchOperation = "70000000-0000-4000-8000-000000000001" as OperationId;
export const batchType = "80000000-abcd-4000-8000-000000000001";
export const batchEmployee = (index: number) =>
  `30000000-0000-4000-8000-${String(index).padStart(12, "0")}`;
export const batchPermissions = ["leave.read", "leave.accrual.post", "leave.year.close"];
export const batchAccess = { companyId: batchCompany, permissions: batchPermissions };
export const batchSelection = { kind: "ACCRUAL" as const, typeId: batchType, period: "2026-09" };
export const batchReviewWire: LeaveBatchReviewDto = {
  ...batchSelection,
  companyDate: "2026-10-15",
  timezone: "Asia/Jakarta",
  totalEmployees: 3,
  employeeIds: [batchEmployee(1), batchEmployee(2), batchEmployee(3)],
  type: {
    id: batchType,
    code: "ANNUAL",
    name: "Annual leave",
    effectiveFrom: "2026-01-01",
    paid: true,
    allowPartialDays: true,
    minServiceMonths: 0,
    allowedContracts: ["PERMANENT"],
    maxRequestDays: 12,
    attachmentRequired: false,
    accrual: { frequency: "MONTHLY", daysPerPeriod: "1", carryLimitDays: "5" },
    active: true,
    version: 4,
    appliedRevision: 2,
  },
};
export const batchWire: LeaveBatchDetailsDto = {
  batch: {
    id: batchId,
    kind: "ACCRUAL",
    typeId: batchType,
    period: "2026-09",
    policy: {
      typeId: batchType,
      code: "ANNUAL",
      revision: 2,
      name: "Annual leave",
      paid: true,
      allowPartialDays: true,
      minServiceMonths: 0,
      allowedContracts: ["PERMANENT"],
      maxRequestDays: 12,
      attachmentRequired: false,
      accrual: { frequency: "MONTHLY", daysPerPeriod: "1", carryLimitDays: "5" },
    },
    policyVersion: 4,
    timezone: "Asia/Jakarta",
    actorId: batchAccount,
    createdAt: "2026-10-15T01:00:00Z",
    reason: "September entitlement",
    totalEmployees: 3,
    jobId: batchJob,
    status: "RUNNING",
    version: 0,
  },
  counts: { applied: 1, unchanged: 0, skipped: 1, failed: 0, completed: 2 },
  attempts: [
    {
      jobId: batchJob,
      number: 1,
      baseCompleted: 0,
      startedAt: "2026-10-15T01:00:00Z",
      reason: "September entitlement",
    },
  ],
  job: {
    jobId: batchJob,
    status: "FAILED",
    completedItems: 2,
    totalItems: 4,
    attempts: 4,
    version: 6,
    cancellationRequested: false,
    failureCode: "job_attempts_exhausted",
  },
  results: {
    items: [
      {
        ordinal: 1,
        employeeId: batchEmployee(1),
        jobId: batchJob,
        status: "APPLIED",
        resourceId: "90000000-0000-4000-8000-000000000001",
        failureCode: null,
        parameters: {},
        completedAt: "2026-10-15T01:00:01Z",
      },
      {
        ordinal: 2,
        employeeId: batchEmployee(2),
        jobId: batchJob,
        status: "SKIPPED",
        resourceId: null,
        failureCode: "leave_employee_ineligible",
        parameters: {},
        completedAt: "2026-10-15T01:00:02Z",
      },
    ],
    nextCursor: null,
  },
  employees: [
    { id: batchEmployee(1), name: "Alex Employee", number: "EMP-001" },
    { id: batchEmployee(2), name: "Robin Employee", number: "EMP-002" },
  ],
  availableActions: ["RESUME"],
};
export const batchReview = toLeaveBatchReview(batchReviewWire, batchCompany, batchSelection);
export const batchDetails = toLeaveBatchDetails(batchWire, batchCompany, batchId, null);
export function deferred<T>() {
  let resolve!: (value: T) => void, reject!: (value: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
