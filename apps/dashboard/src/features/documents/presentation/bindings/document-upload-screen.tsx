import { useContext, useEffect, useMemo, useSyncExternalStore } from "react";
import { useParams } from "react-router-dom";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { DocumentUploadTarget } from "../../domain/entities/document-upload";
import type { DocumentsScreenProps } from "../contracts/documents-screen-props";
import { DocumentUploadController } from "../controllers/document-upload-controller";
import { useDocumentUploadForm } from "../controllers/use-document-upload-form";
import {
  documentFolderPath,
  documentHref,
  documentPath,
  documentRevisionPath,
} from "../models/document-route";
import { documentUploadView } from "../models/document-upload-view";
import { DocumentUploadPage } from "../pages/document-upload-page";

export function DocumentUploadScreen(
  props: DocumentsScreenProps & { kind: DocumentUploadTarget["kind"] },
) {
  const { employeeId = "", documentId = "", revisionId = "" } = useParams();
  const target = useMemo<DocumentUploadTarget>(
    () =>
      props.kind === "new"
        ? { kind: "new", employmentId: employeeId }
        : props.kind === "replacement"
          ? { kind: "replacement", documentId }
          : { kind: "resume", documentId, revisionId },
    [props.kind, employeeId, documentId, revisionId],
  );
  return (
    <DocumentUploadBinding
      key={`${props.accountId}:${props.access.companyId}:${props.kind}:${employeeId}:${documentId}:${revisionId}`}
      {...props}
      target={target}
    />
  );
}
function DocumentUploadBinding(props: DocumentsScreenProps & { target: DocumentUploadTarget }) {
  const { documents, access, target, nextIdentifier, locale } = props;
  const visible = useContext(WorkspaceVisibilityContext);
  const controller = useMemo(
    () => new DocumentUploadController(documents, access, target, nextIdentifier),
    [documents, access, target, nextIdentifier],
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
  useEffect(() => controller.setVisible(visible), [controller, visible]);
  useWorkspaceRevalidation(state.failure);
  const form = useDocumentUploadForm(controller, state);
  const view = useMemo(() => documentUploadView(state, access, locale), [state, access, locale]);
  const backTo = documentHref(
    access.companyId,
    target.kind === "new"
      ? documentFolderPath(target.employmentId)
      : target.kind === "replacement"
        ? documentPath(target.documentId)
        : documentRevisionPath(target.documentId, target.revisionId),
  );
  return (
    <DocumentUploadPage
      state={state}
      form={form}
      view={view}
      kind={target.kind}
      companyName={props.companyName}
      locale={locale}
      backTo={backTo}
      onSelect={controller.selectFile}
      onPause={controller.pause}
      onRetry={controller.retry}
    />
  );
}
