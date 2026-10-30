import { useMemo } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { DocumentsScreenProps } from "../contracts/documents-screen-props";
import { useDocumentMetadata } from "../controllers/use-document-reads";
import { documentMetadataProperties, documentMetadataRows } from "../models/document-metadata-view";
import { documentHref, documentParameters, documentPath } from "../models/document-route";
import { DocumentMetadataHistoryPage } from "../pages/document-metadata-history-page";

export function DocumentMetadataHistoryScreen(props: DocumentsScreenProps) {
  const { documentId = "" } = useParams(),
    [parameters, setParameters] = useSearchParams();
  const after = parameters.get("after"),
    state = useDocumentMetadata(props, documentId, after);
  useWorkspaceRevalidation(state.failure);
  const properties = useMemo(
    () => documentMetadataProperties(state.data?.document.metadata, props.locale),
    [state.data, props.locale],
  );
  const rows = useMemo(
    () =>
      state.data ? documentMetadataRows(state.data.history, props.locale, props.timezone) : [],
    [state.data, props.locale, props.timezone],
  );
  return (
    <DocumentMetadataHistoryPage
      {...props}
      state={state}
      properties={properties}
      rows={rows}
      backTo={documentHref(props.access.companyId, documentPath(documentId))}
      firstPage={after === null}
      onFirst={() => setParameters(documentParameters(props.access.companyId))}
      onNext={() => {
        const cursor = state.data?.history.nextCursor;
        if (cursor) setParameters(documentParameters(props.access.companyId, cursor));
      }}
    />
  );
}
