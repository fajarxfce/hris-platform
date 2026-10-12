import type { HttpClient } from "../../../core/data/http/http-client";
import type { FileInputRepository } from "../../../core/domain/files/file-input-repository";
import type { FileRepository } from "../../../core/domain/files/file-repository";
import { HttpDocumentDataSource } from "../data/datasources/http-document-data-source";
import { RemoteDocumentRepository } from "../data/repositories/remote-document-repository";
import { CancelDocumentUpload } from "../domain/usecases/cancel-document-upload";
import { DownloadDocumentRevision } from "../domain/usecases/download-document-revision";
import { LoadDocumentEmployees } from "../domain/usecases/load-document-employees";
import { LoadDocumentReview } from "../domain/usecases/load-document-review";
import { LoadDocumentRevision } from "../domain/usecases/load-document-revision";
import { LoadEmployeeDocuments } from "../domain/usecases/load-employee-documents";
import { ReleaseDocumentUploadFile } from "../domain/usecases/release-document-upload-file";
import { ReviewDocumentAction } from "../domain/usecases/review-document-action";
import { ReviewDocumentUpload } from "../domain/usecases/review-document-upload";
import { SelectDocumentUploadFile } from "../domain/usecases/select-document-upload-file";
import { UploadDocumentFile } from "../domain/usecases/upload-document-file";
import { ValidateDocumentRevision } from "../domain/usecases/validate-document-revision";

export function createDocumentsFeature(
  http: HttpClient,
  files: Pick<FileRepository, "downloadBinary">,
  input: FileInputRepository,
) {
  const documents = new RemoteDocumentRepository(new HttpDocumentDataSource(http));
  return {
    reviewUpload: new ReviewDocumentUpload(documents),
    selectUploadFile: new SelectDocumentUploadFile(input),
    releaseUploadFile: new ReleaseDocumentUploadFile(input),
    uploadFile: new UploadDocumentFile(documents, input),
    reviewAction: new ReviewDocumentAction(documents),
    validate: new ValidateDocumentRevision(documents),
    cancelUpload: new CancelDocumentUpload(documents),
    loadEmployees: new LoadDocumentEmployees(documents),
    loadFolder: new LoadEmployeeDocuments(documents),
    loadReview: new LoadDocumentReview(documents),
    loadRevision: new LoadDocumentRevision(documents),
    download: new DownloadDocumentRevision(documents, files),
  };
}
