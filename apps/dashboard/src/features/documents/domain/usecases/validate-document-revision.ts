import type { OperationId } from "../../../../core/domain/identifiers";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { DocumentRevisionSnapshot } from "../entities/document-revision-action";
import { prepareDocumentAction } from "../policies/document-action-policy";
import type { DocumentRepository } from "../repositories/document-repository";

export class ValidateDocumentRevision {
  constructor(private readonly documents: DocumentRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    revision: DocumentRevisionSnapshot,
    reason: string,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    const prepared = prepareDocumentAction(access, operation, revision, "validate", reason);
    return prepared.ok
      ? this.documents.validate(access.companyId, operation, prepared.value, signal)
      : Promise.resolve(prepared);
  }
}
