import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type {
  PayrollAmendment,
  PayrollAmendmentDetails,
  PayrollAmendmentPage,
  PayrollAmendmentSummary,
  PayrollAmendmentTerms,
} from "../../domain/entities/payroll-amendment";
import type { PayrollAmendmentSearch } from "../../domain/entities/payroll-amendment-search";
import { normalizePayrollInputTerms } from "../../domain/policies/payroll-input-validation";
import type {
  PayrollAmendmentDetailsDto,
  PayrollAmendmentDto,
  PayrollAmendmentHistoryDto,
  PayrollAmendmentPageDto,
  PayrollAmendmentSummaryDto,
  PayrollAmendmentTermsDto,
} from "../models/payroll-amendment-dto";
import { toCompensationEmployee, toCompensationTerms } from "./compensation-mapper";
import { toPayrollAmendmentCalculationSummary } from "./payroll-amendment-calculation-mapper";
export function toPayrollAmendmentSummary(
  raw: PayrollAmendmentSummaryDto,
  company: CompanyId,
): PayrollAmendmentSummary {
  const allowed = {
    SAVED: ["DRAFT"],
    CALCULATED: ["CALCULATED"],
    CALCULATION_FAILED: ["DRAFT"],
    SUBMITTED: ["IN_REVIEW"],
    APPROVED: ["IN_REVIEW", "APPROVED"],
    REJECTED: ["REJECTED"],
    WITHDRAWN: ["CALCULATED"],
    CANCELLED: ["CANCELLED"],
    PUBLICATION_STARTED: ["PUBLISHING"],
    PUBLISHED: ["PUBLISHED"],
    PUBLICATION_STOPPED: ["APPROVED"],
  };
  if (
    !allowed[raw.action].includes(raw.status) ||
    Date.parse(raw.recordedAt) < Date.parse(raw.createdAt) ||
    (raw.version === 0 &&
      (raw.action !== "SAVED" ||
        raw.latestCalculationId !== null ||
        raw.latestReviewId !== null ||
        raw.latestPublicationId !== null)) ||
    (!["DRAFT", "CANCELLED"].includes(raw.status) && raw.latestCalculationId === null) ||
    (["PUBLISHING", "PUBLISHED"].includes(raw.status) && raw.latestPublicationId === null)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...raw,
    companyId: company,
    id: raw.id.toLowerCase(),
    firstAssessmentId: raw.firstAssessmentId.toLowerCase(),
    employee: toCompensationEmployee(raw.employee),
    actorId: raw.actorId.toLowerCase(),
    latestCalculationId: raw.latestCalculationId?.toLowerCase() ?? null,
    latestReviewId: raw.latestReviewId?.toLowerCase() ?? null,
    latestPublicationId: raw.latestPublicationId?.toLowerCase() ?? null,
  });
}
export function toPayrollAmendmentTerms(
  raw: PayrollAmendmentTermsDto,
  first: string,
): PayrollAmendmentTerms {
  const sources = raw.sourceVersions.map((source) =>
      Object.freeze({ ...source, assessmentId: source.assessmentId.toLowerCase() }),
    ),
    corrections = raw.corrections.map((row) =>
      Object.freeze({
        assessmentId: row.assessmentId.toLowerCase(),
        compensation: row.compensation ? toCompensationTerms(row.compensation) : null,
        input: row.input ? normalizePayrollInputTerms(row.input) : null,
      }),
    );
  if (
    new Set(sources.map((row) => row.assessmentId)).size !== sources.length ||
    new Set(corrections.map((row) => row.assessmentId)).size !== corrections.length ||
    !corrections.some((row) => row.assessmentId === first) ||
    corrections.some(
      (row) =>
        (row.compensation === null && row.input === null) ||
        !sources.some((source) => source.assessmentId === row.assessmentId),
    )
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    sourceVersions: Object.freeze(sources),
    corrections: Object.freeze(corrections),
  });
}
export function toPayrollAmendment(
  raw: PayrollAmendmentDto,
  company: CompanyId,
  id: string,
  revision?: number,
): PayrollAmendment {
  const summary = toPayrollAmendmentSummary(raw.summary, company);
  if (summary.id !== id || (revision !== undefined && summary.version !== revision))
    throw new InvalidHttpResponseError();
  return Object.freeze({
    summary,
    terms: toPayrollAmendmentTerms(raw.terms, summary.firstAssessmentId),
  });
}
export function toPayrollAmendmentPage(
  raw: PayrollAmendmentPageDto,
  company: CompanyId,
  search: PayrollAmendmentSearch,
): PayrollAmendmentPage {
  const items = raw.items.map((row) => toPayrollAmendmentSummary(row, company)),
    nextCursor = raw.nextCursor?.toLowerCase() ?? null;
  if (
    items.some(
      (row, index) =>
        Number(row.firstMonth.slice(0, 4)) !== search.year ||
        (search.employeeId !== null && row.employee.id !== search.employeeId) ||
        (search.status !== null && row.status !== search.status) ||
        (search.after !== null && row.id <= search.after) ||
        (index > 0 && row.id <= (items[index - 1]?.id ?? "")),
    ) ||
    (nextCursor !== null && (items.length !== 50 || nextCursor !== items.at(-1)?.id))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor });
}
export function toPayrollAmendmentHistory(
  raw: PayrollAmendmentHistoryDto,
  company: CompanyId,
  id: string,
  after: number | null,
): PayrollAmendmentPage {
  const items = raw.items.map((row) => toPayrollAmendmentSummary(row, company));
  if (
    items.some((row, index) => row.id !== id || row.version !== (after ?? -1) + index + 1) ||
    (raw.nextCursor !== null &&
      (items.length !== 25 || Number(raw.nextCursor) !== items.at(-1)?.version))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: raw.nextCursor });
}
export function toPayrollAmendmentDetails(
  raw: PayrollAmendmentDetailsDto,
  company: CompanyId,
  id: string,
): PayrollAmendmentDetails {
  const amendment = toPayrollAmendment(raw.amendment, company, id),
    history = toPayrollAmendmentHistory(raw.history, company, id, null),
    latestCalculation = raw.latestCalculation
      ? toPayrollAmendmentCalculationSummary(raw.latestCalculation, id)
      : null,
    review = raw.latestReview,
    latestReview = review
      ? Object.freeze({
          ...review,
          id: review.id.toLowerCase(),
          amendmentId: review.amendmentId.toLowerCase(),
          calculationId: review.calculationId.toLowerCase(),
          approvalId: review.approvalId.toLowerCase(),
          submittedBy: review.submittedBy.toLowerCase(),
        })
      : null,
    summary = amendment.summary;
  if (
    (latestCalculation?.id ?? null) !== summary.latestCalculationId ||
    (latestReview?.id ?? null) !== summary.latestReviewId ||
    (latestCalculation !== null && latestCalculation.draftVersion >= summary.version) ||
    (latestReview !== null &&
      (latestReview.amendmentId !== id || latestReview.amendmentVersion >= summary.version)) ||
    new Set(raw.availableActions).size !== raw.availableActions.length ||
    history.items.length === 0 ||
    history.items.some(
      (row) =>
        row.version > summary.version ||
        row.employee.id !== summary.employee.id ||
        row.firstAssessmentId !== summary.firstAssessmentId ||
        row.firstMonth !== summary.firstMonth ||
        (row.version === summary.version && JSON.stringify(row) !== JSON.stringify(summary)),
    )
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    amendment,
    history,
    latestCalculation,
    latestReview,
    availableActions: Object.freeze([...raw.availableActions]),
  });
}
