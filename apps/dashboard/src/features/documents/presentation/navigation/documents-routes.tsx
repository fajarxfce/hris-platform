import { Navigate, Route, Routes, useLocation, useSearchParams } from "react-router-dom";
import { DocumentActionScreen } from "../bindings/document-action-screen";
import { DocumentEmployeesScreen } from "../bindings/document-employees-screen";
import { DocumentFolderScreen } from "../bindings/document-folder-screen";
import { DocumentReviewScreen } from "../bindings/document-review-screen";
import { DocumentRevisionScreen } from "../bindings/document-revision-screen";
import { DocumentUploadScreen } from "../bindings/document-upload-screen";
import type { DocumentsScreenProps } from "../contracts/documents-screen-props";
import { documentHref } from "../models/document-route";

export function DocumentsRoutes(props: DocumentsScreenProps) {
  const [parameters] = useSearchParams(),
    location = useLocation();
  // Pin deep links before any feature read; changing company discards old resource references.
  if (parameters.get("company") !== props.access.companyId) {
    const scoped = new URLSearchParams(parameters);
    scoped.set("company", props.access.companyId);
    return (
      <Navigate
        replace
        to={
          parameters.has("company")
            ? documentHref(props.access.companyId)
            : `${location.pathname}?${scoped}`
        }
      />
    );
  }
  return (
    <Routes>
      <Route index element={<DocumentEmployeesScreen {...props} />} />
      <Route
        path="employees/:employeeId/upload"
        element={<DocumentUploadScreen {...props} kind="new" />}
      />
      <Route
        path=":documentId/upload"
        element={<DocumentUploadScreen {...props} kind="replacement" />}
      />
      <Route
        path=":documentId/revisions/:revisionId/upload"
        element={<DocumentUploadScreen {...props} kind="resume" />}
      />
      <Route path="employees/:employeeId" element={<DocumentFolderScreen {...props} />} />
      <Route
        path=":documentId/revisions/:revisionId/:intent"
        element={<DocumentActionScreen {...props} />}
      />
      <Route
        path=":documentId/revisions/:revisionId"
        element={<DocumentRevisionScreen {...props} />}
      />
      <Route path=":documentId" element={<DocumentReviewScreen {...props} />} />
      <Route path="*" element={<Navigate to={documentHref(props.access.companyId)} replace />} />
    </Routes>
  );
}
