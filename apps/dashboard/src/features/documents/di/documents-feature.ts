import type { HttpClient } from "../../../core/data/http/http-client";
import type { FileInputRepository } from "../../../core/domain/files/file-input-repository";
import type { FileRepository } from "../../../core/domain/files/file-repository";
import { HttpDocumentDataSource } from "../data/datasources/http-document-data-source";
import { HttpDocumentMetadataDataSource } from "../data/datasources/http-document-metadata-data-source";
import { HttpDocumentRetentionDataSource } from "../data/datasources/http-document-retention-data-source";
import { RemoteDocumentMetadataRepository } from "../data/repositories/remote-document-metadata-repository";
import { RemoteDocumentRepository } from "../data/repositories/remote-document-repository";
import { RemoteDocumentRetentionRepository } from "../data/repositories/remote-document-retention-repository";
import { CancelDocumentUpload } from "../domain/usecases/cancel-document-upload";
import { ChangeDocumentRetention } from "../domain/usecases/change-document-retention";
import { DownloadDocumentRevision } from "../domain/usecases/download-document-revision";
import { LoadDocumentEmployees } from "../domain/usecases/load-document-employees";
import { LoadDocumentMetadata } from "../domain/usecases/load-document-metadata";
import { LoadDocumentRetention } from "../domain/usecases/load-document-retention";
import { LoadDocumentRetentionPolicies } from "../domain/usecases/load-document-retention-policies";
import { LoadDocumentRetentionPolicy } from "../domain/usecases/load-document-retention-policy";
import { LoadDocumentReview } from "../domain/usecases/load-document-review";
import { LoadDocumentRevision } from "../domain/usecases/load-document-revision";
import { LoadEmployeeDocuments } from "../domain/usecases/load-employee-documents";
import { ReleaseDocumentUploadFile } from "../domain/usecases/release-document-upload-file";
import { RetireDocumentRevision } from "../domain/usecases/retire-document-revision";
import { ReviewDocumentAction } from "../domain/usecases/review-document-action";
import { ReviewDocumentMetadata } from "../domain/usecases/review-document-metadata";
import { ReviewDocumentRetentionChange } from "../domain/usecases/review-document-retention-change";
import { ReviewDocumentRetentionPolicy } from "../domain/usecases/review-document-retention-policy";
import { ReviewDocumentRetirement } from "../domain/usecases/review-document-retirement";
import { ReviewDocumentUpload } from "../domain/usecases/review-document-upload";
import { SaveDocumentMetadata } from "../domain/usecases/save-document-metadata";
import { SaveDocumentRetentionPolicy } from "../domain/usecases/save-document-retention-policy";
import { SelectDocumentUploadFile } from "../domain/usecases/select-document-upload-file";
import { UploadDocumentFile } from "../domain/usecases/upload-document-file";
import { ValidateDocumentRevision } from "../domain/usecases/validate-document-revision";

export function createDocumentsFeature(
  http: HttpClient,
  files: Pick<FileRepository, "downloadBinary">,
  input: FileInputRepository,
) {
  const documents = new RemoteDocumentRepository(new HttpDocumentDataSource(http));
  const retention = new RemoteDocumentRetentionRepository(
    new HttpDocumentRetentionDataSource(http),
  );
  const metadata = new RemoteDocumentMetadataRepository(new HttpDocumentMetadataDataSource(http));
  return {
    metadata: {
      load: new LoadDocumentMetadata(documents, metadata),
      review: new ReviewDocumentMetadata(documents),
      save: new SaveDocumentMetadata(metadata),
    },
    retention: {
      reviewRetirement: new ReviewDocumentRetirement(retention),
      retireRevision: new RetireDocumentRevision(retention),
      load: new LoadDocumentRetention(retention),
      reviewChange: new ReviewDocumentRetentionChange(retention),
      change: new ChangeDocumentRetention(retention),
      loadPolicies: new LoadDocumentRetentionPolicies(retention),
      loadPolicy: new LoadDocumentRetentionPolicy(retention),
      reviewPolicy: new ReviewDocumentRetentionPolicy(retention),
      savePolicy: new SaveDocumentRetentionPolicy(retention),
    },
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
