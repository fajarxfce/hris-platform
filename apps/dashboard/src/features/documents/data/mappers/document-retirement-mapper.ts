import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type {
  DocumentRetirementCommand,
  DocumentRetirementReview,
} from "../../domain/entities/document-retirement";
import type {
  DocumentRetirementReceiptDto,
  DocumentRetirementReviewDto,
} from "../models/document-retirement-dto";
import { toDocumentRetention } from "./document-retention-state-mapper";

export function toDocumentRetirementReview(
  raw: DocumentRetirementReviewDto,
  company: CompanyId,
  documentId: string,
  revisionId: string,
): DocumentRetirementReview {
  const retention = toDocumentRetention(raw.retention, company, documentId);
  if (
    raw.revisionId.toLowerCase() !== revisionId ||
    (raw.published && raw.status !== "READY") ||
    (raw.blockingReason === null &&
      (raw.status !== "READY" ||
        retention.version === null ||
        retention.version >= 9999 ||
        retention.legalHold ||
        !retention.archive?.eligibleAt ||
        Date.parse(retention.archive.eligibleAt) > Date.parse(raw.reviewedAt)))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ ...raw, revisionId, retention });
}
export function toDocumentRetirementReceipt(
  raw: DocumentRetirementReceiptDto,
  input: DocumentRetirementCommand,
) {
  if (
    raw.id.toLowerCase() !== input.revisionId ||
    raw.version !== input.expectedRevisionVersion + 1
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ id: raw.id.toLowerCase(), version: raw.version });
}
