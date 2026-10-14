import type {
  PayrollFinalizationDetailsDto,
  PayrollFinalizationDto,
  PayrollFinalizationPreparationDto,
} from "../../src/features/payroll/data/models/payroll-finalization-dto";
import { calculationId, calculationRun } from "./payroll-calculations";
import { payrollReview, reviewId } from "./payroll-reviews";
export const finalizationId = "ef000000-0000-4000-8000-000000000001",
  finalizationJob = "ef100000-0000-4000-8000-000000000001",
  finalizationActor = "ef200000-0000-4000-8000-000000000001";
export function payrollFinalization(
  changes: Partial<PayrollFinalizationDto> = {},
): PayrollFinalizationDto {
  return {
    id: finalizationId,
    runId: calculationId,
    reviewId,
    runVersion: 1,
    reviewVersion: 2,
    approvalVersion: 3,
    number: 1,
    jobId: finalizationJob,
    actorId: finalizationActor,
    requestedAt: "2026-10-01T01:00:00Z",
    reason: "Publish independently approved payroll",
    companyCode: "DEMO",
    companyName: "Demo Company",
    publishedAt: null,
    ...changes,
  };
}
export function payrollFinalizationPreparation(): PayrollFinalizationPreparationDto {
  return {
    run: calculationRun(),
    totals: payrollReview().totals,
    latestReview: payrollReview({ status: "APPROVED", version: 2 }),
    approvalVersion: 3,
    latestFinalization: null,
    latestJobStatus: null,
    blockingIssues: [],
    availableActions: ["START"],
  };
}
export function payrollFinalizationDetails(
  status: PayrollFinalizationDetailsDto["job"]["status"] = "QUEUED",
): PayrollFinalizationDetailsDto {
  return {
    finalization: payrollFinalization({
      publishedAt: status === "SUCCEEDED" ? "2026-10-01T01:00:03Z" : null,
    }),
    run: calculationRun(
      status === "SUCCEEDED" ? { status: "FINALIZED", version: 2, finalizationId } : {},
    ),
    totals: payrollReview().totals,
    job: {
      jobId: finalizationJob,
      status,
      completedItems: status === "SUCCEEDED" ? 1 : 0,
      totalItems: 1,
      attempts: status === "QUEUED" ? 0 : 1,
      version: status === "QUEUED" ? 0 : 2,
      cancellationRequested: status === "CANCELLED",
      failureCode: status === "FAILED" ? "stale_employment_version" : null,
    },
  };
}
