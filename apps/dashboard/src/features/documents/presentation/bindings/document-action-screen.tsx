import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import {
  type DocumentRevisionIntent,
  documentRevisionIntents,
} from "../../domain/entities/document-revision-action";
import type { DocumentsScreenProps } from "../contracts/documents-screen-props";
import { DocumentActionController } from "../controllers/document-action-controller";
import { useDocumentActionForm } from "../controllers/use-document-action-form";
import { documentActionCopy, documentActionView } from "../models/document-action-view";
import { documentHref, documentRevisionPath } from "../models/document-route";
import { DocumentActionPage } from "../pages/document-action-page";

export function DocumentActionScreen(props: DocumentsScreenProps) {
  const { documentId = "", revisionId = "", intent = "" } = useParams();
  const backTo = documentHref(props.access.companyId, documentRevisionPath(documentId, revisionId));
  if (!documentRevisionIntents.includes(intent as DocumentRevisionIntent))
    return <Navigate to={backTo} replace />;
  return (
    <DocumentActionBinding
      key={`${props.accountId}:${props.access.companyId}:${documentId}:${revisionId}:${intent}`}
      {...props}
      documentId={documentId}
      revisionId={revisionId}
      intent={intent as DocumentRevisionIntent}
      backTo={backTo}
    />
  );
}
function DocumentActionBinding(
  props: DocumentsScreenProps & {
    documentId: string;
    revisionId: string;
    intent: DocumentRevisionIntent;
    backTo: string;
  },
) {
  const { documents, access, documentId, revisionId, intent, nextIdentifier, locale } = props;
  const controller = useMemo(
    () =>
      new DocumentActionController(
        documents,
        access,
        documentId,
        revisionId,
        intent,
        nextIdentifier,
      ),
    [documents, access, documentId, revisionId, intent, nextIdentifier],
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
  const form = useDocumentActionForm(controller, state);
  const view = useMemo(
    () => (state.review ? documentActionView(state.review, locale) : null),
    [state.review, locale],
  );
  return (
    <DocumentActionPage
      state={state}
      form={form}
      view={view}
      copy={documentActionCopy(intent, locale)}
      companyName={props.companyName}
      locale={locale}
      backTo={props.backTo}
      onRetry={controller.retry}
    />
  );
}
