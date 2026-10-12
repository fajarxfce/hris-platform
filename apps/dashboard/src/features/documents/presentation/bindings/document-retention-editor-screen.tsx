import { useEffect, useMemo, useSyncExternalStore } from "react";
import { useParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { DocumentsScreenProps } from "../contracts/documents-screen-props";
import { DocumentRetentionEditorController } from "../controllers/document-retention-editor-controller";
import { useDocumentRetentionForm } from "../controllers/use-document-retention-form";
import { documentRetentionPolicyPath } from "../models/document-retention-route";
import { documentRetentionPolicyProperties } from "../models/document-retention-view";
import { documentHref } from "../models/document-route";
import { DocumentRetentionEditorPage } from "../pages/document-retention-editor-page";

export function DocumentRetentionEditorScreen(props: DocumentsScreenProps) {
  const { classification = "" } = useParams();
  return (
    <DocumentRetentionEditorBinding
      key={`${props.accountId}:${props.access.companyId}:${classification}`}
      {...props}
      classification={classification.toUpperCase()}
    />
  );
}
function DocumentRetentionEditorBinding(props: DocumentsScreenProps & { classification: string }) {
  const { documents, access, classification, nextIdentifier, locale, timezone } = props;
  const controller = useMemo(
    () =>
      new DocumentRetentionEditorController(
        documents.retention,
        access,
        classification,
        nextIdentifier,
      ),
    [documents, access, classification, nextIdentifier],
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
  const form = useDocumentRetentionForm(controller, state);
  const properties = useMemo(
    () =>
      state.selection ? documentRetentionPolicyProperties(state.selection, locale, timezone) : [],
    [state.selection, locale, timezone],
  );
  return (
    <DocumentRetentionEditorPage
      {...props}
      state={state}
      form={form}
      properties={properties}
      onRetry={controller.retry}
      backTo={documentHref(access.companyId, documentRetentionPolicyPath(classification))}
    />
  );
}
