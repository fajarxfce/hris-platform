import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { DocumentRevisionChange } from "../../domain/entities/document-revision-action";
import type { DocumentMutationReceiptDto } from "../models/document-mutation-dto";

export function toDocumentMutation(
  raw: DocumentMutationReceiptDto,
  change: DocumentRevisionChange,
): MutationReceipt {
  if (raw.id.toLowerCase() !== change.id || raw.version !== change.version + 1)
    throw new InvalidHttpResponseError();
  return Object.freeze({ id: raw.id.toLowerCase(), version: raw.version });
}
