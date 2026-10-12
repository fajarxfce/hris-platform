import { useMemo } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canBrowseDocuments } from "../../domain/policies/document-read-policy";
import { availableDocumentRetentionIntents } from "../../domain/policies/document-retention-change-policy";
import type { DocumentsScreenProps } from "../contracts/documents-screen-props";
import { useDocumentRetention } from "../controllers/use-document-reads";
import { documentRetentionLifecycleMessages } from "../i18n/document-retention-lifecycle-messages";
import {
  documentRetentionChangeProperties,
  documentRetentionHistoryRows,
  documentRetentionProperties,
} from "../models/document-retention-lifecycle-view";
import {
  documentRetentionChangePath,
  documentRetentionPoliciesPath,
} from "../models/document-retention-route";
import { documentHref, documentParameters, documentPath } from "../models/document-route";
import { DocumentRetentionPage } from "../pages/document-retention-page";

export function DocumentRetentionScreen(props: DocumentsScreenProps) {
  const { documentId = "" } = useParams(),
    [parameters, setParameters] = useSearchParams();
  const after = parameters.get("after"),
    selected = parameters.get("change"),
    state = useDocumentRetention(props, documentId, after);
  useWorkspaceRevalidation(state.failure);
  const view = useMemo(() => {
    const text = documentRetentionLifecycleMessages(props.locale),
      change = state.data?.history.items.find((entry) => String(entry.state.version) === selected);
    return {
      properties: state.data
        ? documentRetentionProperties(state.data.current, props.locale, props.timezone)
        : [],
      rows: state.data
        ? documentRetentionHistoryRows(state.data, props.locale, props.timezone)
        : [],
      change: change
        ? documentRetentionChangeProperties(change, props.locale, props.timezone)
        : null,
      actions: state.data
        ? availableDocumentRetentionIntents(state.data.current).map((intent) => ({
            to: documentHref(
              props.access.companyId,
              documentRetentionChangePath(documentId, intent),
            ),
            label: text.actions[intent].title,
          }))
        : [],
    };
  }, [state.data, selected, props.locale, props.timezone, props.access.companyId, documentId]);
  return (
    <DocumentRetentionPage
      {...props}
      {...view}
      state={state}
      backTo={documentHref(props.access.companyId, documentRetentionPoliciesPath)}
      documentTo={
        state.data && canBrowseDocuments(props.access.permissions)
          ? documentHref(props.access.companyId, documentPath(documentId))
          : null
      }
      firstPage={after === null}
      onFirst={() => setParameters(documentParameters(props.access.companyId))}
      onNext={() => {
        if (state.data?.history.nextCursor)
          setParameters(documentParameters(props.access.companyId, state.data.history.nextCursor));
      }}
      onOpenChange={(version) => {
        const next = documentParameters(props.access.companyId, after);
        next.set("change", version);
        setParameters(next);
      }}
      onCloseChange={() =>
        setParameters(documentParameters(props.access.companyId, after), { replace: true })
      }
    />
  );
}
