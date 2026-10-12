import type { ChangeDocumentRetention } from "../../domain/usecases/change-document-retention";
import type { LoadDocumentRetention } from "../../domain/usecases/load-document-retention";
import type { LoadDocumentRetentionPolicies } from "../../domain/usecases/load-document-retention-policies";
import type { LoadDocumentRetentionPolicy } from "../../domain/usecases/load-document-retention-policy";
import type { ReviewDocumentRetentionChange } from "../../domain/usecases/review-document-retention-change";
import type { ReviewDocumentRetentionPolicy } from "../../domain/usecases/review-document-retention-policy";
import type { SaveDocumentRetentionPolicy } from "../../domain/usecases/save-document-retention-policy";

export type DocumentRetentionUseCases = Readonly<{
  load: Pick<LoadDocumentRetention, "execute">;
  reviewChange: Pick<ReviewDocumentRetentionChange, "execute">;
  change: Pick<ChangeDocumentRetention, "execute">;
  loadPolicies: Pick<LoadDocumentRetentionPolicies, "execute">;
  loadPolicy: Pick<LoadDocumentRetentionPolicy, "execute">;
  reviewPolicy: Pick<ReviewDocumentRetentionPolicy, "execute">;
  savePolicy: Pick<SaveDocumentRetentionPolicy, "execute">;
}>;
