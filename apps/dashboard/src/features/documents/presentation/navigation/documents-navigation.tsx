import { Archive20Regular, DocumentFolder20Regular } from "@fluentui/react-icons";
import type { AppNavigationItem } from "../../../../core/presentation/components/app-portal-shell";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canBrowseDocuments } from "../../domain/policies/document-read-policy";
import { canManageDocumentRetention } from "../../domain/policies/document-retention-policy";
import { documentMessages } from "../i18n/document-messages";
import { documentRetentionMessages } from "../i18n/document-retention-messages";
import { documentRetentionPoliciesPath } from "../models/document-retention-route";
import { documentsPath } from "../models/document-route";

export function documentsNavigation(
  access: CompanyAccess | null,
  locale: Locale,
): readonly AppNavigationItem[] {
  return canBrowseDocuments(access?.permissions ?? [])
    ? [
        {
          to: documentsPath,
          label: documentMessages(locale).title,
          icon: <DocumentFolder20Regular />,
        },
      ]
    : [];
}

export function documentRetentionNavigation(
  access: CompanyAccess | null,
  locale: Locale,
): readonly AppNavigationItem[] {
  return canManageDocumentRetention(access?.permissions ?? [])
    ? [
        {
          to: documentRetentionPoliciesPath,
          label: documentRetentionMessages(locale).title,
          icon: <Archive20Regular />,
        },
      ]
    : [];
}
