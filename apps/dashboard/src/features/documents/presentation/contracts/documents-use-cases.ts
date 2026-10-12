import type { DownloadDocumentRevision } from "../../domain/usecases/download-document-revision";
import type { LoadDocumentEmployees } from "../../domain/usecases/load-document-employees";
import type { LoadDocumentReview } from "../../domain/usecases/load-document-review";
import type { LoadDocumentRevision } from "../../domain/usecases/load-document-revision";
import type { LoadEmployeeDocuments } from "../../domain/usecases/load-employee-documents";
import type { ReviewDocumentAction } from "../../domain/usecases/review-document-action";
import type { ValidateDocumentRevision } from "../../domain/usecases/validate-document-revision";

export type DocumentsUseCases = Readonly<{
  loadEmployees: Pick<LoadDocumentEmployees, "execute">;
  loadFolder: Pick<LoadEmployeeDocuments, "execute">;
  loadReview: Pick<LoadDocumentReview, "execute">;
  loadRevision: Pick<LoadDocumentRevision, "execute">;
  download: Pick<DownloadDocumentRevision, "execute">;
  reviewAction: Pick<ReviewDocumentAction, "execute">;
  validate: Pick<ValidateDocumentRevision, "execute">;
  cancelUpload: Pick<CancelDocumentUpload, "execute">;
}>;

import type { CancelDocumentUpload } from "../../domain/usecases/cancel-document-upload";
