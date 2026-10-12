import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { DocumentRetentionIntent } from "../../domain/entities/document-retention";
import { validDocumentRetentionIntent } from "../../domain/policies/document-retention-change-policy";
import type { DocumentsScreenProps } from "../contracts/documents-screen-props";
import { DocumentRetentionChangeController } from "../controllers/document-retention-change-controller";
import { useDocumentRetentionChangeForm } from "../controllers/use-document-retention-change-form";
import { documentRetentionProperties } from "../models/document-retention-lifecycle-view";
import { documentRetentionPath } from "../models/document-retention-route";
import { documentHref } from "../models/document-route";
import { DocumentRetentionChangePage } from "../pages/document-retention-change-page";

export function DocumentRetentionChangeScreen(props: DocumentsScreenProps) {
  const { documentId = "", intent = "" } = useParams();
  const backTo = documentHref(props.access.companyId, documentRetentionPath(documentId));
  if (!validDocumentRetentionIntent(intent)) return <Navigate to={backTo} replace />;
  return (
    <DocumentRetentionChangeBinding
      key={`${props.accountId}:${props.access.companyId}:${documentId}:${intent}`}
      {...props}
      documentId={documentId}
      intent={intent}
      backTo={backTo}
    />
  );
}
function DocumentRetentionChangeBinding(
  props: DocumentsScreenProps & {
    documentId: string;
    intent: DocumentRetentionIntent;
    backTo: string;
  },
) {
  const { documents, access, documentId, intent, nextIdentifier, locale, timezone } = props;
  const controller = useMemo(
    () =>
      new DocumentRetentionChangeController(
        documents.retention,
        access,
        documentId,
        intent,
        nextIdentifier,
      ),
    [documents, access, documentId, intent, nextIdentifier],
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
  const form = useDocumentRetentionChangeForm(controller, state),
    properties = useMemo(
      () => (state.review ? documentRetentionProperties(state.review, locale, timezone) : []),
      [state.review, locale, timezone],
    );
  return (
    <DocumentRetentionChangePage
      {...props}
      state={state}
      form={form}
      properties={properties}
      onRetry={controller.retry}
    />
  );
}
