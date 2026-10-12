import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { DocumentRevisionChange } from "../../domain/entities/document-revision-action";
import type { DocumentMutationReceiptDto } from "../models/document-mutation-dto";
import type { DocumentUploadReceiptDto } from "../models/document-upload-dto";

export function toDocumentMutation(
  raw: DocumentMutationReceiptDto,
  change: DocumentRevisionChange,
): MutationReceipt {
  return toDocumentReceipt(raw, change.id, change.version + 1);
}

export function toDocumentReceipt(
  raw: DocumentUploadReceiptDto,
  revisionId: string,
  version: number,
): MutationReceipt {
  if (raw.id.toLowerCase() !== revisionId || raw.version !== version)
    throw new InvalidHttpResponseError();
  return Object.freeze({ id: raw.id.toLowerCase(), version: raw.version });
}
