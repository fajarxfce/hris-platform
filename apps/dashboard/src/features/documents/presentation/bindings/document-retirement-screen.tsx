import { useEffect, useMemo, useSyncExternalStore } from "react";
import { useParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { DocumentsScreenProps } from "../contracts/documents-screen-props";
import { DocumentRetirementController } from "../controllers/document-retirement-controller";
import { useDocumentRetirementForm } from "../controllers/use-document-retirement-form";
import { documentRetentionPath } from "../models/document-retention-route";
import { documentRetirementView } from "../models/document-retirement-view";
import { documentHref } from "../models/document-route";
import { DocumentRetirementPage } from "../pages/document-retirement-page";

export function DocumentRetirementScreen(props: DocumentsScreenProps) {
  const { documentId = "", revisionId = "" } = useParams();
  return (
    <DocumentRetirementBinding
      key={`${props.accountId}:${props.access.companyId}:${documentId}:${revisionId}`}
      {...props}
      documentId={documentId}
      revisionId={revisionId}
    />
  );
}
function DocumentRetirementBinding(
  props: DocumentsScreenProps & { documentId: string; revisionId: string },
) {
  const { documents, access, documentId, revisionId, nextIdentifier, locale, timezone } = props;
  const controller = useMemo(
    () =>
      new DocumentRetirementController(
        documents.retention,
        access,
        documentId,
        revisionId,
        nextIdentifier,
      ),
    [documents, access, documentId, revisionId, nextIdentifier],
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
  const form = useDocumentRetirementForm(controller, state);
  const view = useMemo(
    () => (state.review ? documentRetirementView(state.review, locale, timezone) : null),
    [state.review, locale, timezone],
  );
  return (
    <DocumentRetirementPage
      {...props}
      state={state}
      form={form}
      view={view}
      backTo={documentHref(access.companyId, documentRetentionPath(documentId))}
      onRetry={controller.retry}
    />
  );
}
