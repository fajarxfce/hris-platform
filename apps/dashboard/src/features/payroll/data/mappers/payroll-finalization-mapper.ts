import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { PayrollFinalization } from "../../domain/entities/payroll-finalization";
import type { PayrollFinalizationDetails } from "../../domain/entities/payroll-finalization-details";
import type { PayrollFinalizationPreparation } from "../../domain/entities/payroll-finalization-preparation";
import type {
  PayrollPublicationPage,
  PayrollPublicationSearch,
} from "../../domain/entities/payroll-publication-search";
import type {
  PayrollFinalizationDetailsDto,
  PayrollFinalizationDto,
  PayrollFinalizationPreparationDto,
  PayrollFinalizationsDto,
  PayrollPublicationPageDto,
} from "../models/payroll-finalization-dto";
import { toPayrollCalculationFailure, toPayrollRun } from "./payroll-calculation-mapper";
import { toPayrollReview } from "./payroll-review-mapper";
export function toPayrollFinalization(
  raw: PayrollFinalizationDto,
  company: CompanyId,
): PayrollFinalization {
  return Object.freeze({
    ...raw,
    companyId: company,
    id: raw.id.toLowerCase(),
    runId: raw.runId.toLowerCase(),
    reviewId: raw.reviewId.toLowerCase(),
    jobId: raw.jobId.toLowerCase(),
    actorId: raw.actorId.toLowerCase(),
  });
}
export function toPayrollPublicationPage(
  raw: PayrollPublicationPageDto,
  company: CompanyId,
  search: PayrollPublicationSearch,
): PayrollPublicationPage {
  const items = raw.items.map((row) => toPayrollRun(row, company)),
    nextCursor = raw.nextCursor?.toLowerCase() ?? null;
  if (
    items.some(
      (row, index) =>
        !["CALCULATED", "FINALIZED"].includes(row.status) ||
        row.earningsMonth < search.from ||
        row.earningsMonth > search.until ||
        (search.status !== null && row.status !== search.status) ||
        (index > 0 && row.id <= (items[index - 1]?.id ?? "")) ||
        (search.after !== null && row.id <= search.after),
    ) ||
    (nextCursor !== null && (items.length !== 50 || nextCursor !== items.at(-1)?.id))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor });
}
export function toPayrollFinalizations(
  raw: PayrollFinalizationsDto,
  company: CompanyId,
  run: string,
): readonly PayrollFinalization[] {
  const items = raw.items.map((row) => toPayrollFinalization(row, company));
  if (
    raw.nextCursor !== null ||
    new Set(items.map((row) => row.id)).size !== items.length ||
    new Set(items.map((row) => row.jobId)).size !== items.length ||
    items.some((row, index) => row.runId !== run || row.number !== index + 1) ||
    items.filter((row) => row.publishedAt !== null).length > 1
  )
    throw new InvalidHttpResponseError();
  return Object.freeze(items);
}
export function toPayrollFinalizationPreparation(
  raw: PayrollFinalizationPreparationDto,
  company: CompanyId,
  id: string,
): PayrollFinalizationPreparation {
  const run = toPayrollRun(raw.run, company),
    latestReview = raw.latestReview ? toPayrollReview(raw.latestReview, company) : null,
    latestFinalization = raw.latestFinalization
      ? toPayrollFinalization(raw.latestFinalization, company)
      : null;
  if (
    run.id !== id ||
    raw.totals.employeeCount !== run.succeeded ||
    (latestReview !== null && latestReview.runId !== id) ||
    (latestFinalization !== null && latestFinalization.runId !== id) ||
    (latestFinalization === null) !== (raw.latestJobStatus === null) ||
    (latestFinalization !== null &&
      ((raw.latestJobStatus === "SUCCEEDED") !== (latestFinalization.publishedAt !== null) ||
        (latestFinalization.publishedAt !== null &&
          (run.status !== "FINALIZED" || run.finalizationId !== latestFinalization.id)))) ||
    (latestReview === null && raw.approvalVersion !== null) ||
    (raw.availableActions.includes("START") &&
      (run.status !== "CALCULATED" ||
        run.failed !== 0 ||
        run.succeeded !== run.totalEmployees ||
        latestReview?.status !== "APPROVED" ||
        latestReview.runVersion !== run.version ||
        raw.approvalVersion === null ||
        raw.approvalVersion < 1 ||
        raw.blockingIssues.length !== 0 ||
        (latestFinalization !== null &&
          (latestFinalization.number >= 8 ||
            !["FAILED", "CANCELLED"].includes(raw.latestJobStatus ?? "")))))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    run,
    latestReview,
    latestFinalization,
    approvalVersion: raw.approvalVersion,
    latestJobStatus: raw.latestJobStatus,
    totals: Object.freeze({ ...raw.totals }),
    blockingIssues: Object.freeze(raw.blockingIssues.map(toPayrollCalculationFailure)),
    availableActions: Object.freeze([...raw.availableActions]),
  });
}
export function toPayrollFinalizationDetails(
  raw: PayrollFinalizationDetailsDto,
  company: CompanyId,
  id: string,
): PayrollFinalizationDetails {
  const finalization = toPayrollFinalization(raw.finalization, company),
    run = toPayrollRun(raw.run, company),
    job = Object.freeze({ ...raw.job, jobId: raw.job.jobId.toLowerCase() });
  if (
    finalization.id !== id ||
    run.id !== finalization.runId ||
    job.jobId !== finalization.jobId ||
    raw.totals.employeeCount !== run.totalEmployees ||
    (job.status === "SUCCEEDED") !== (finalization.publishedAt !== null) ||
    (finalization.publishedAt !== null &&
      (run.status !== "FINALIZED" || run.finalizationId !== id || job.completedItems !== 1))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ finalization, run, job, totals: Object.freeze({ ...raw.totals }) });
}
