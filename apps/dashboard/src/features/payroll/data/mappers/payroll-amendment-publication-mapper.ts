import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { PayrollAmendmentPublicationDetails } from "../../domain/entities/payroll-amendment-publication-details";
import type { PayrollAmendmentPublicationDetailsDto } from "../models/payroll-amendment-publication-dto";
import { toPayrollAmendmentSummary } from "./payroll-amendment-mapper";

export function toPayrollAmendmentPublicationDetails(
  raw: PayrollAmendmentPublicationDetailsDto,
  company: CompanyId,
  id: string,
): PayrollAmendmentPublicationDetails {
  const publication = Object.freeze({
      ...raw.publication,
      id: raw.publication.id.toLowerCase(),
      amendmentId: raw.publication.amendmentId.toLowerCase(),
      reviewId: raw.publication.reviewId.toLowerCase(),
      calculationId: raw.publication.calculationId.toLowerCase(),
      jobId: raw.publication.jobId.toLowerCase(),
      actorId: raw.publication.actorId.toLowerCase(),
      completedBy: raw.publication.completedBy?.toLowerCase() ?? null,
    }),
    amendment = toPayrollAmendmentSummary(raw.amendment, company),
    job = Object.freeze({ ...raw.job, jobId: raw.job.jobId.toLowerCase() });
  const pending = publication.status === "PENDING",
    published = publication.status === "PUBLISHED";
  if (
    publication.amendmentId !== id ||
    amendment.id !== id ||
    publication.jobId !== job.jobId ||
    publication.amendmentVersion >= amendment.version ||
    Date.parse(publication.requestedAt) < Date.parse(amendment.createdAt) ||
    (publication.completedAt !== null &&
      Date.parse(publication.completedAt) < Date.parse(publication.requestedAt)) ||
    (pending
      ? publication.completedAt !== null ||
        publication.completedBy !== null ||
        publication.failureCode !== null ||
        job.completedItems !== 0 ||
        job.status === "SUCCEEDED" ||
        amendment.latestPublicationId !== publication.id ||
        amendment.status !== "PUBLISHING" ||
        amendment.version !== publication.amendmentVersion + 1
      : publication.completedAt === null ||
        publication.completedBy === null ||
        amendment.version < publication.amendmentVersion + 2) ||
    (published &&
      (job.status !== "SUCCEEDED" ||
        job.completedItems !== 1 ||
        publication.failureCode !== null ||
        publication.completedBy !== publication.actorId ||
        amendment.status !== "PUBLISHED" ||
        amendment.latestPublicationId !== publication.id)) ||
    (publication.status === "STOPPED" &&
      (publication.failureCode === null ||
        !["FAILED", "CANCELLED"].includes(job.status) ||
        job.completedItems !== 0)) ||
    (raw.availableActions.includes("RECOVER") &&
      (!pending || !["FAILED", "CANCELLED"].includes(job.status)))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    publication,
    amendment,
    job,
    availableActions: Object.freeze([...raw.availableActions]),
  });
}
export function toPayrollAmendmentPublications(
  raw: readonly PayrollAmendmentPublicationDetailsDto[],
  company: CompanyId,
  id: string,
): readonly PayrollAmendmentPublicationDetails[] {
  const rows = raw.map((value) => toPayrollAmendmentPublicationDetails(value, company, id)),
    last = rows.at(-1);
  if (
    new Set(rows.map((row) => row.publication.id)).size !== rows.length ||
    new Set(rows.map((row) => row.job.jobId)).size !== rows.length ||
    rows.some(
      (row, index) =>
        row.publication.number !== index + 1 ||
        JSON.stringify(row.amendment) !== JSON.stringify(last?.amendment) ||
        (index < rows.length - 1 && row.publication.status !== "STOPPED"),
    ) ||
    (last && last.amendment.latestPublicationId !== last.publication.id)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze(rows);
}
