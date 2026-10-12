import { useMemo } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { DocumentsScreenProps } from "../contracts/documents-screen-props";
import { useDocumentFolder } from "../controllers/use-document-reads";
import { documentHref, documentParameters, documentPath } from "../models/document-route";
import { documentRows } from "../models/document-view";
import { DocumentFolderPage } from "../pages/document-folder-page";

export function DocumentFolderScreen(props: DocumentsScreenProps) {
  const { employeeId = "" } = useParams(),
    [parameters, setParameters] = useSearchParams(),
    navigate = useNavigate();
  const after = parameters.get("after"),
    state = useDocumentFolder(props, employeeId, after);
  const rows = useMemo(
    () => (state.data ? documentRows(state.data, props.locale, props.timezone) : []),
    [state.data, props.locale, props.timezone],
  );
  useWorkspaceRevalidation(state.failure);
  return (
    <DocumentFolderPage
      {...props}
      state={state}
      rows={rows}
      backTo={documentHref(props.access.companyId)}
      firstPage={after === null}
      onFirst={() => setParameters(documentParameters(props.access.companyId))}
      onNext={() => {
        if (state.data?.documents.nextCursor)
          setParameters(
            documentParameters(props.access.companyId, state.data.documents.nextCursor),
          );
      }}
      onOpen={(id) => navigate(documentHref(props.access.companyId, documentPath(id)))}
    />
  );
}
