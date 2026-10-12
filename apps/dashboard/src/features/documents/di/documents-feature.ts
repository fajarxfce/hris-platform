import type { HttpClient } from "../../../core/data/http/http-client";
import type { FileRepository } from "../../../core/domain/files/file-repository";
import { HttpDocumentDataSource } from "../data/datasources/http-document-data-source";
import { RemoteDocumentRepository } from "../data/repositories/remote-document-repository";
import { DownloadDocumentRevision } from "../domain/usecases/download-document-revision";
import { LoadDocumentEmployees } from "../domain/usecases/load-document-employees";
import { LoadDocumentReview } from "../domain/usecases/load-document-review";
import { LoadDocumentRevision } from "../domain/usecases/load-document-revision";
import { LoadEmployeeDocuments } from "../domain/usecases/load-employee-documents";

export function createDocumentsFeature(
  http: HttpClient,
  files: Pick<FileRepository, "downloadBinary">,
) {
  const documents = new RemoteDocumentRepository(new HttpDocumentDataSource(http));
  return {
    loadEmployees: new LoadDocumentEmployees(documents),
    loadFolder: new LoadEmployeeDocuments(documents),
    loadReview: new LoadDocumentReview(documents),
    loadRevision: new LoadDocumentRevision(documents),
    download: new DownloadDocumentRevision(documents, files),
  };
}
