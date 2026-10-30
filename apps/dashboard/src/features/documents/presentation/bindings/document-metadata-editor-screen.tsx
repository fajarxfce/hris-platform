import { useEffect, useMemo, useSyncExternalStore } from "react";
import { useParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { DocumentsScreenProps } from "../contracts/documents-screen-props";
import { DocumentMetadataEditorController } from "../controllers/document-metadata-editor-controller";
import { useDocumentMetadataForm } from "../controllers/use-document-metadata-form";
import { documentMetadataEditorProperties } from "../models/document-metadata-view";
import { documentHref, documentPath } from "../models/document-route";
import { DocumentMetadataEditorPage } from "../pages/document-metadata-editor-page";

export function DocumentMetadataEditorScreen(props: DocumentsScreenProps) {
  const { documentId = "" } = useParams();
  return (
    <DocumentMetadataEditorBinding
      key={`${props.accountId}:${props.access.companyId}:${documentId}`}
      {...props}
      documentId={documentId.toLowerCase()}
    />
  );
}
function DocumentMetadataEditorBinding(props: DocumentsScreenProps & { documentId: string }) {
  const { documents, access, documentId, nextIdentifier, locale } = props;
  const controller = useMemo(
    () =>
      new DocumentMetadataEditorController(documents.metadata, access, documentId, nextIdentifier),
    [documents, access, documentId, nextIdentifier],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => {
    controller.activate();
    return controller.deactivate;
  }, [controller]);
  useWorkspaceRevalidation(state.failure);
  const form = useDocumentMetadataForm(controller, state);
  const properties = useMemo(
    () => (state.selection ? documentMetadataEditorProperties(state.selection, locale) : []),
    [state.selection, locale],
  );
  return (
    <DocumentMetadataEditorPage
      {...props}
      state={state}
      form={form}
      properties={properties}
      onRetry={controller.retry}
      backTo={documentHref(access.companyId, documentPath(documentId))}
    />
  );
}
