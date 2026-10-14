import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import type { PayrollCalculationReview } from "../../domain/entities/payroll-calculation-review";
import type { PayrollRun } from "../../domain/entities/payroll-run";
import type { PayrollRunDetails } from "../../domain/entities/payroll-run-details";
import type { PayrollRunItem } from "../../domain/entities/payroll-run-item";
import type {
  PayrollCalculationReviewDto,
  PayrollRunDetailsDto,
  PayrollRunDto,
  PayrollRunItemDto,
  PayrollRunsDto,
} from "../models/payroll-calculation-dto";
import { toPayrollPeriod } from "./payroll-period-mapper";
export function toPayrollCalculationFailure(
  raw: Failure & Readonly<{ kind: string }>,
): Failure & Readonly<{ kind: string }> {
  return Object.freeze({
    kind: raw.kind,
    code: raw.code,
    fields: Object.freeze({ ...raw.fields }),
    parameters: Object.freeze({ ...raw.parameters }),
  });
}
export function toPayrollCalculationReview(
  raw: PayrollCalculationReviewDto,
  company: CompanyId,
  id: string,
): PayrollCalculationReview {
  const period = toPayrollPeriod(raw.period, company),
    source = raw.source,
    policy = raw.policy;
  if (
    period.id !== id ||
    (source !== null && source.month !== period.earningsMonth) ||
    (policy !== null &&
      (policy.effectiveFrom > period.earningsMonth ||
        policy.effectiveUntil < period.earningsMonth ||
        policy.appliedRevision > policy.version)) ||
    (raw.availableActions.includes("START") &&
      (period.status !== "DRAFT" ||
        period.currentRunId !== null ||
        raw.blockingIssues.length > 0 ||
        !source?.closed ||
        source.jobId === null ||
        source.timezone !== period.timezone ||
        policy === null ||
        raw.runCount >= 20))
  )
    throw new InvalidHttpResponseError();
  if (new Set(raw.blockingIssues.map((issue) => issue.code)).size !== raw.blockingIssues.length)
    throw new InvalidHttpResponseError();
  return Object.freeze({
    period,
    source: source
      ? Object.freeze({
          ...source,
          id: source.id.toLowerCase(),
          jobId: source.jobId?.toLowerCase() ?? null,
        })
      : null,
    policy: policy ? Object.freeze({ ...policy }) : null,
    runCount: raw.runCount,
    blockingIssues: Object.freeze(raw.blockingIssues.map(toPayrollCalculationFailure)),
    availableActions: Object.freeze([...raw.availableActions]),
  });
}
export function toPayrollRun(raw: PayrollRunDto, company: CompanyId): PayrollRun {
  if (
    raw.taxMonth !== raw.earningsMonth ||
    raw.incomeDueDate.slice(0, 7) !== raw.earningsMonth ||
    raw.processed !== raw.succeeded + raw.failed ||
    raw.processed > raw.totalEmployees ||
    ((raw.status === "CALCULATED" || raw.status === "FINALIZED") &&
      raw.processed !== raw.totalEmployees) ||
    (raw.status === "FINALIZED") !== (raw.finalizationId !== null)
  )
    throw new InvalidHttpResponseError();
  try {
    new Intl.DateTimeFormat("en", { timeZone: raw.timezone });
  } catch {
    throw new InvalidHttpResponseError();
  }
  return Object.freeze({
    ...raw,
    companyId: company,
    id: raw.id.toLowerCase(),
    periodId: raw.periodId.toLowerCase(),
    actorId: raw.actorId.toLowerCase(),
    jobId: raw.jobId.toLowerCase(),
    workJobId: raw.workJobId.toLowerCase(),
    finalizationId: raw.finalizationId?.toLowerCase() ?? null,
  });
}
export function toPayrollRuns(
  raw: PayrollRunsDto,
  company: CompanyId,
  period: string,
): readonly PayrollRun[] {
  const runs = raw.items.map((row) => toPayrollRun(row, company));
  if (
    raw.nextCursor !== null ||
    new Set(runs.map((row) => row.id)).size !== runs.length ||
    runs.some((row, index) => row.periodId !== period || row.number !== index + 1)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze(runs);
}
export function toPayrollRunItem(raw: PayrollRunItemDto): PayrollRunItem {
  const t = raw.target;
  if (
    (t.inputId === null) !== (t.inputRevision === null) ||
    (t.taxOpeningId === null) !== (t.taxOpeningRevision === null) ||
    [raw.taxableGross, raw.withheld, raw.takeHome].some(
      (amount) => (amount === null) !== (raw.failure !== null),
    )
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...raw,
    target: Object.freeze({
      ...t,
      employeeId: t.employeeId.toLowerCase(),
      inputId: t.inputId?.toLowerCase() ?? null,
      taxOpeningId: t.taxOpeningId?.toLowerCase() ?? null,
      previousAssessmentId: t.previousAssessmentId?.toLowerCase() ?? null,
    }),
    jobId: raw.jobId.toLowerCase(),
    failure: raw.failure ? toPayrollCalculationFailure(raw.failure) : null,
  });
}
export function toPayrollRunDetails(
  raw: PayrollRunDetailsDto,
  company: CompanyId,
  id: string,
  after: string | null,
  limit: number,
): PayrollRunDetails {
  const run = toPayrollRun(raw.run, company),
    period = toPayrollPeriod(raw.period, company),
    items = raw.results.items.map(toPayrollRunItem),
    attempts = raw.attempts.map((row) => Object.freeze({ ...row, jobId: row.jobId.toLowerCase() })),
    job = Object.freeze({ ...raw.job, jobId: raw.job.jobId.toLowerCase() }),
    last = attempts.at(-1),
    cursor = raw.results.nextCursor;
  if (
    run.id !== id ||
    period.id !== run.periodId ||
    period.earningsMonth !== run.earningsMonth ||
    period.participantCount !== run.totalEmployees ||
    run.jobId !== job.jobId ||
    last?.jobId !== job.jobId ||
    job.completedItems > job.totalItems ||
    job.totalItems !== run.totalEmployees - (last?.baseCompleted ?? 0) + 1 ||
    new Set(attempts.map((row) => row.jobId)).size !== attempts.length ||
    attempts.some(
      (row, index) =>
        row.number !== index + 1 ||
        row.baseCompleted > run.processed ||
        row.baseCompleted < (attempts[index - 1]?.baseCompleted ?? 0),
    )
  )
    throw new InvalidHttpResponseError();
  if (
    items.length > limit ||
    items.some(
      (row, index) =>
        row.target.ordinal <= Number(items[index - 1]?.target.ordinal ?? after ?? 0) ||
        row.target.ordinal > run.processed ||
        !attempts.some((attempt) => attempt.jobId === row.jobId),
    ) ||
    new Set(items.map((row) => row.target.employeeId)).size !== items.length ||
    (cursor !== null &&
      (items.length !== limit ||
        cursor !== String(items.at(-1)?.target.ordinal) ||
        Number(cursor) >= run.processed))
  )
    throw new InvalidHttpResponseError();
  if (new Set(raw.availableActions).size !== raw.availableActions.length)
    throw new InvalidHttpResponseError();
  return Object.freeze({
    run,
    period,
    attempts: Object.freeze(attempts),
    job,
    results: Object.freeze({ items: Object.freeze(items), nextCursor: cursor }),
    availableActions: Object.freeze([...raw.availableActions]),
  });
}
