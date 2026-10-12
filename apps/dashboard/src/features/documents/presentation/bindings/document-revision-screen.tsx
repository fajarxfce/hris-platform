import { useContext, useEffect, useMemo, useSyncExternalStore } from "react";
import { useParams } from "react-router-dom";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { DocumentRevisionReview } from "../../domain/entities/document-revision-review";
import type { DocumentsScreenProps } from "../contracts/documents-screen-props";
import { DocumentDownloadController } from "../controllers/document-download-controller";
import { useDocumentRevision } from "../controllers/use-document-reads";
import { documentMessages } from "../i18n/document-messages";
import { documentActionLinks } from "../models/document-action-view";
import { documentHref, documentPath } from "../models/document-route";
import { documentRevisionView } from "../models/document-view";
import { DocumentRevisionContent } from "../pages/document-revision-content";
import { DocumentPageFrame } from "../widgets/document-page-frame";

export function DocumentRevisionScreen(props: DocumentsScreenProps) {
  const { documentId = "", revisionId = "" } = useParams();
  const state = useDocumentRevision(props, documentId, revisionId),
    text = documentMessages(props.locale);
  useWorkspaceRevalidation(state.failure);
  return (
    <DocumentPageFrame
      {...state}
      title={text.revisionDetails}
      context={`${props.companyName} / ${text.title}`}
      locale={props.locale}
      back={{
        to: documentHref(props.access.companyId, documentPath(documentId)),
        label: text.backDocument,
      }}
    >
      {state.data && <DocumentRevisionBinding {...props} review={state.data} />}
    </DocumentPageFrame>
  );
}

function DocumentRevisionBinding(props: DocumentsScreenProps & { review: DocumentRevisionReview }) {
  const visible = useContext(WorkspaceVisibilityContext);
  const controller = useMemo(
    () =>
      new DocumentDownloadController(
        props.documents.download,
        props.access,
        props.review.revision.documentId,
        props.review.revision.id,
      ),
    [props.documents.download, props.access, props.review],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => {
    if (visible) controller.activate();
    return controller.deactivate;
  }, [controller, visible]);
  const view = useMemo(
    () => documentRevisionView(props.review, props.locale, props.timezone),
    [props.review, props.locale, props.timezone],
  );
  const actions = useMemo(
    () => documentActionLinks(props.access, props.review.revision, props.locale),
    [props.access, props.review.revision, props.locale],
  );
  useWorkspaceRevalidation(state.failure);
  return (
    <DocumentRevisionContent
      view={view}
      state={state}
      locale={props.locale}
      ready={props.review.revision.status === "READY"}
      actions={actions}
      onDownload={() => void controller.start()}
      onCancel={controller.cancel}
    />
  );
}
