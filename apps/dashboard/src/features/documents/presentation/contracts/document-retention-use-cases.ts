import type { LoadDocumentRetentionPolicies } from "../../domain/usecases/load-document-retention-policies";
import type { LoadDocumentRetentionPolicy } from "../../domain/usecases/load-document-retention-policy";
import type { ReviewDocumentRetentionPolicy } from "../../domain/usecases/review-document-retention-policy";
import type { SaveDocumentRetentionPolicy } from "../../domain/usecases/save-document-retention-policy";

export type DocumentRetentionUseCases = Readonly<{
  loadPolicies: Pick<LoadDocumentRetentionPolicies, "execute">;
  loadPolicy: Pick<LoadDocumentRetentionPolicy, "execute">;
  reviewPolicy: Pick<ReviewDocumentRetentionPolicy, "execute">;
  savePolicy: Pick<SaveDocumentRetentionPolicy, "execute">;
}>;
