import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canPerformDocumentAction } from "../../domain/policies/document-action-policy";
import { documentMessages } from "../i18n/document-messages";
import { documentUploadMessages } from "../i18n/document-upload-messages";
import { documentActionPath, documentHref, documentRevisionPath } from "./document-route";
import type { DocumentUploadState } from "./document-upload-state";
import { documentSize } from "./document-view";

export function documentUploadView(
  state: DocumentUploadState,
  access: CompanyAccess,
  locale: Locale,
) {
  const text = documentUploadMessages(locale),
    documents = documentMessages(locale),
    review = state.review;
  const properties: { label: string; value: string }[] = review
    ? [
        {
          label: text.employee,
          value: `${review.employee.name} · ${review.employee.employeeNumber}`,
        },
      ]
    : [];
  if (review && review.kind !== "new")
    properties.push(
      { label: text.document, value: review.document.title },
      { label: text.classification, value: documents[review.document.classification] },
    );
  if (review?.kind === "resume")
    properties.push({ label: text.file, value: review.revision.fileName });
  const completion = state.completion;
  const size = state.file?.byteLength ?? completion?.size ?? null;
  return {
    properties,
    selectedFile: state.file
      ? `${state.file.name} · ${documentSize(state.file.byteLength, locale)}`
      : null,
    progress:
      size !== null && state.uploadedBytes !== null && (review !== null || completion !== null)
        ? {
            value: state.uploadedBytes / size,
            label: `${text.progress}: ${documentSize(state.uploadedBytes, locale)} / ${documentSize(size, locale)}`,
          }
        : null,
    revisionTo: completion
      ? documentHref(access.companyId, documentRevisionPath(completion.documentId, completion.id))
      : null,
    validateTo:
      completion && canPerformDocumentAction(access, completion, "validate")
        ? documentHref(
            access.companyId,
            documentActionPath(completion.documentId, completion.id, "validate"),
          )
        : null,
    completedMessage: completion?.status === "UPLOADING" ? text.complete : text.inspected,
  };
}
