import { useMemo } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canReviseDocumentRetentionPolicy } from "../../domain/policies/document-retention-policy";
import type { DocumentsScreenProps } from "../contracts/documents-screen-props";
import { useDocumentRetentionPolicy } from "../controllers/use-document-reads";
import { documentMessages } from "../i18n/document-messages";
import { documentRetentionMessages } from "../i18n/document-retention-messages";
import {
  documentRetentionPoliciesPath,
  documentRetentionPolicyPath,
} from "../models/document-retention-route";
import {
  documentRetentionPolicyHistoryRows,
  documentRetentionPolicyProperties,
} from "../models/document-retention-view";
import { documentHref, documentParameters } from "../models/document-route";
import { DocumentRetentionPolicyPage } from "../pages/document-retention-policy-page";

export function DocumentRetentionPolicyScreen(props: DocumentsScreenProps) {
  const { classification = "" } = useParams(),
    [parameters, setParameters] = useSearchParams();
  const after = parameters.get("after"),
    state = useDocumentRetentionPolicy(props, classification.toUpperCase(), after);
  useWorkspaceRevalidation(state.failure);
  const view = useMemo(
    () => ({
      properties: state.data
        ? documentRetentionPolicyProperties(state.data.selection, props.locale, props.timezone)
        : [],
      rows: state.data
        ? documentRetentionPolicyHistoryRows(state.data, props.locale, props.timezone)
        : [],
    }),
    [state.data, props.locale, props.timezone],
  );
  return (
    <DocumentRetentionPolicyPage
      {...props}
      {...view}
      state={state}
      title={
        state.data
          ? documentMessages(props.locale)[state.data.selection.classification]
          : documentRetentionMessages(props.locale).editor
      }
      backTo={documentHref(props.access.companyId, documentRetentionPoliciesPath)}
      editTo={
        state.data && canReviseDocumentRetentionPolicy(state.data.selection)
          ? documentHref(
              props.access.companyId,
              `${documentRetentionPolicyPath(classification)}/edit`,
            )
          : null
      }
      firstPage={after === null}
      onFirst={() => setParameters(documentParameters(props.access.companyId))}
      onNext={() => {
        if (state.data?.history.nextCursor)
          setParameters(documentParameters(props.access.companyId, state.data.history.nextCursor));
      }}
    />
  );
}
