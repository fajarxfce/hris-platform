import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { DocumentRevision } from "../../domain/entities/document-revision";
import {
  type DocumentRevisionIntent,
  documentRevisionIntents,
} from "../../domain/entities/document-revision-action";
import { canPerformDocumentAction } from "../../domain/policies/document-action-policy";
import { documentActionMessages } from "../i18n/document-action-messages";
import { documentMessages } from "../i18n/document-messages";
import { documentActionPath, documentHref } from "./document-route";
import { documentSize } from "./document-view";

export function documentActionLinks(
  access: CompanyAccess,
  revision: DocumentRevision,
  locale: Locale,
) {
  const text = documentActionMessages(locale);
  return documentRevisionIntents
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
