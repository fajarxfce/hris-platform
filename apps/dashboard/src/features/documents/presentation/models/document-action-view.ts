import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { DocumentRevision } from "../../domain/entities/document-revision";
import {
  type DocumentRevisionIntent,
  documentRevisionIntents,
} from "../../domain/entities/document-revision-action";
import { canPerformDocumentAction } from "../../domain/policies/document-action-policy";
import { canResumeDocument } from "../../domain/policies/document-upload-policy";
import { documentActionMessages } from "../i18n/document-action-messages";
import { documentMessages } from "../i18n/document-messages";
import { documentUploadMessages } from "../i18n/document-upload-messages";
import { documentActionPath, documentHref, documentRevisionPath } from "./document-route";
import { documentSize } from "./document-view";

export function documentActionLinks(
  access: CompanyAccess,
  revision: DocumentRevision,
  locale: Locale,
) {
  const text = documentActionMessages(locale);
  const links = documentRevisionIntents
    .filter((intent) => canPerformDocumentAction(access, revision, intent))
    .map((intent) => ({
      to: documentHref(
        access.companyId,
        documentActionPath(revision.documentId, revision.id, intent),
      ),
      label:
        intent === "cancel"
          ? text.cancel
          : revision.validationAttempts > 0
            ? text.retryValidation
            : text.validate,
    }));
  if (canResumeDocument(access, revision))
    links.unshift({
      to: documentHref(
        access.companyId,
        `${documentRevisionPath(revision.documentId, revision.id)}/upload`,
      ),
      label: documentUploadMessages(locale).resume,
    });
  return links;
}

export function documentActionView(revision: DocumentRevision, locale: Locale) {
  const text = documentMessages(locale),
    actions = documentActionMessages(locale);
  return [
    { label: text.fileName, value: revision.fileName },
    { label: text.status, value: text[revision.status] },
    { label: text.size, value: documentSize(revision.size, locale) },
    { label: text.uploaded, value: documentSize(revision.uploadedBytes, locale) },
    { label: actions.version, value: String(revision.version) },
  ];
}
export function documentActionCopy(intent: DocumentRevisionIntent, locale: Locale) {
  const text = documentActionMessages(locale);
  return intent === "validate"
    ? { title: text.validate, hint: text.validateHint, saved: text.validateSaved }
    : { title: text.cancel, hint: text.cancelHint, saved: text.cancelSaved };
}
