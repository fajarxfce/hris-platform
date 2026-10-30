import { useMemo } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canManageDocumentRetention } from "../../domain/policies/document-retention-policy";
import { canCreateCompanyDocument } from "../../domain/policies/document-upload-policy";
import type { DocumentsScreenProps } from "../contracts/documents-screen-props";
import { useDocumentReview } from "../controllers/use-document-reads";
import { documentRetentionPath } from "../models/document-retention-route";
import {
  documentFolderPath,
  documentHref,
  documentParameters,
  documentPath,
  documentRevisionPath,
} from "../models/document-route";
import { documentProperties, documentRevisionRows } from "../models/document-view";
import { DocumentReviewPage } from "../pages/document-review-page";

export function DocumentReviewScreen(props: DocumentsScreenProps) {
  const { documentId = "" } = useParams(),
    [parameters, setParameters] = useSearchParams(),
    navigate = useNavigate();
  const after = parameters.get("after"),
    state = useDocumentReview(props, documentId, after);
  const view = useMemo(
    () => ({
      properties: state.data
        ? documentProperties(state.data.document, props.locale, props.timezone)
        : [],
      rows: state.data
        ? documentRevisionRows(state.data.revisions, props.locale, props.timezone)
        : [],
    }),
    [state.data, props.locale, props.timezone],
  );
  useWorkspaceRevalidation(state.failure);
  return (
    <DocumentReviewPage
      {...props}
      {...view}
      state={state}
      metadataTo={
        state.data?.document.metadataSupported
          ? documentHref(props.access.companyId, `${documentPath(documentId)}/metadata`)
          : null
      }
      metadataEditTo={
        state.data?.document.metadataSupported &&
        (state.data.document.metadataVersion ?? 999) < 999 &&
        canCreateCompanyDocument(props.access.permissions)
          ? documentHref(props.access.companyId, `${documentPath(documentId)}/metadata/edit`)
          : null
      }
      retentionTo={
        state.data && canManageDocumentRetention(props.access.permissions)
          ? documentHref(props.access.companyId, documentRetentionPath(documentId))
          : null
      }
      uploadTo={
        state.data &&
        state.data.document.revisionCount < 100 &&
        canCreateCompanyDocument(props.access.permissions)
          ? documentHref(props.access.companyId, `${documentPath(documentId)}/upload`)
          : null
      }
      firstPage={after === null}
      backTo={documentHref(
        props.access.companyId,
        state.data ? documentFolderPath(state.data.document.employmentId) : undefined,
      )}
      publishedTo={
        state.data?.document.currentRevisionId
          ? documentHref(
              props.access.companyId,
              documentRevisionPath(documentId, state.data.document.currentRevisionId),
            )
          : null
      }
      onFirst={() => setParameters(documentParameters(props.access.companyId))}
      onNext={() => {
        if (state.data?.revisions.nextCursor)
          setParameters(
            documentParameters(props.access.companyId, state.data.revisions.nextCursor),
          );
      }}
      onOpen={(id) =>
        navigate(documentHref(props.access.companyId, documentRevisionPath(documentId, id)))
      }
    />
  );
}
