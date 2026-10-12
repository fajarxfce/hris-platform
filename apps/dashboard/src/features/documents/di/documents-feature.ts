import type { HttpClient } from "../../../core/data/http/http-client";
import type { FileRepository } from "../../../core/domain/files/file-repository";
import { HttpDocumentDataSource } from "../data/datasources/http-document-data-source";
import { RemoteDocumentRepository } from "../data/repositories/remote-document-repository";
import { CancelDocumentUpload } from "../domain/usecases/cancel-document-upload";
import { DownloadDocumentRevision } from "../domain/usecases/download-document-revision";
import { LoadDocumentEmployees } from "../domain/usecases/load-document-employees";
import { LoadDocumentReview } from "../domain/usecases/load-document-review";
import { LoadDocumentRevision } from "../domain/usecases/load-document-revision";
import { LoadEmployeeDocuments } from "../domain/usecases/load-employee-documents";
import { ReviewDocumentAction } from "../domain/usecases/review-document-action";
import { ValidateDocumentRevision } from "../domain/usecases/validate-document-revision";

export function createDocumentsFeature(
  http: HttpClient,
  files: Pick<FileRepository, "downloadBinary">,
) {
  const documents = new RemoteDocumentRepository(new HttpDocumentDataSource(http));
  return {
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
