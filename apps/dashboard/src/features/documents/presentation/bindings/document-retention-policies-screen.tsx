import { useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { DocumentsScreenProps } from "../contracts/documents-screen-props";
import { useDocumentRetentionPolicies } from "../controllers/use-document-reads";
import { useDocumentRetentionLookup } from "../controllers/use-document-retention-lookup";
import {
  documentRetentionPath,
  documentRetentionPolicyPath,
} from "../models/document-retention-route";
import { documentRetentionPolicyRows } from "../models/document-retention-view";
import { documentHref } from "../models/document-route";
import { DocumentRetentionPoliciesPage } from "../pages/document-retention-policies-page";

export function DocumentRetentionPoliciesScreen(props: DocumentsScreenProps) {
  const state = useDocumentRetentionPolicies(props),
    navigate = useNavigate();
  useWorkspaceRevalidation(state.failure);
  const lookup = useDocumentRetentionLookup((documentId) =>
    navigate(documentHref(props.access.companyId, documentRetentionPath(documentId))),
  );
  const rows = useMemo(
    () => (state.data ? documentRetentionPolicyRows(state.data, props.locale) : []),
    [state.data, props.locale],
  );
  return (
    <DocumentRetentionPoliciesPage
      {...props}
      state={state}
      lookup={lookup}
      rows={rows}
      onOpen={(classification) =>
        navigate(documentHref(props.access.companyId, documentRetentionPolicyPath(classification)))
      }
    />
  );
}
