import type { DownloadDocumentRevision } from "../../domain/usecases/download-document-revision";
import type { LoadDocumentEmployees } from "../../domain/usecases/load-document-employees";
import type { LoadDocumentReview } from "../../domain/usecases/load-document-review";
import type { LoadDocumentRevision } from "../../domain/usecases/load-document-revision";
import type { LoadEmployeeDocuments } from "../../domain/usecases/load-employee-documents";
import type { ReleaseDocumentUploadFile } from "../../domain/usecases/release-document-upload-file";
import type { ReviewDocumentAction } from "../../domain/usecases/review-document-action";
import type { ReviewDocumentUpload } from "../../domain/usecases/review-document-upload";
import type { SelectDocumentUploadFile } from "../../domain/usecases/select-document-upload-file";
import type { UploadDocumentFile } from "../../domain/usecases/upload-document-file";
import type { ValidateDocumentRevision } from "../../domain/usecases/validate-document-revision";

export type DocumentsUseCases = Readonly<{
  reviewUpload: Pick<ReviewDocumentUpload, "execute">;
  selectUploadFile: Pick<SelectDocumentUploadFile, "execute">;
  releaseUploadFile: Pick<ReleaseDocumentUploadFile, "execute">;
  uploadFile: Pick<UploadDocumentFile, "execute">;
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
