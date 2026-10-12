import { Navigate, Route, Routes, useLocation, useSearchParams } from "react-router-dom";
import { DocumentActionScreen } from "../bindings/document-action-screen";
import { DocumentEmployeesScreen } from "../bindings/document-employees-screen";
import { DocumentFolderScreen } from "../bindings/document-folder-screen";
import { DocumentRetentionChangeScreen } from "../bindings/document-retention-change-screen";
import { DocumentRetentionEditorScreen } from "../bindings/document-retention-editor-screen";
import { DocumentRetentionPoliciesScreen } from "../bindings/document-retention-policies-screen";
import { DocumentRetentionPolicyScreen } from "../bindings/document-retention-policy-screen";
import { DocumentRetentionScreen } from "../bindings/document-retention-screen";
import { DocumentRetirementScreen } from "../bindings/document-retirement-screen";
import { DocumentReviewScreen } from "../bindings/document-review-screen";
import { DocumentRevisionScreen } from "../bindings/document-revision-screen";
import { DocumentUploadScreen } from "../bindings/document-upload-screen";
import type { DocumentsScreenProps } from "../contracts/documents-screen-props";
import {
  documentRetentionPoliciesPath,
  isDocumentRetentionPath,
  isLegacyDocumentRetentionPath,
} from "../models/document-retention-route";
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
            ? documentHref(
                props.access.companyId,
                isDocumentRetentionPath(location.pathname)
                  ? documentRetentionPoliciesPath
                  : undefined,
              )
            : `${location.pathname}?${scoped}`
        }
      />
    );
  }
  if (isLegacyDocumentRetentionPath(location.pathname)) {
    return (
      <Navigate
        replace
        to={`${location.pathname.replace("/documents/retention-policies", documentRetentionPoliciesPath)}${location.search}`}
      />
    );
  }
  return (
    <Routes>
      <Route index element={<DocumentEmployeesScreen {...props} />} />
      <Route
        path="retention"
        element={
          <DocumentRetentionPoliciesScreen
            key={`${props.accountId}:${props.access.companyId}`}
            {...props}
          />
        }
      />
      <Route
        path="retention/policies"
        element={
          <DocumentRetentionPoliciesScreen
            key={`${props.accountId}:${props.access.companyId}`}
            {...props}
          />
        }
      />
      <Route
        path="retention/policies/:classification"
        element={<DocumentRetentionPolicyScreen {...props} />}
      />
      <Route
        path="retention/policies/:classification/edit"
        element={<DocumentRetentionEditorScreen {...props} />}
      />
      <Route
        path="retention/documents/:documentId"
        element={<DocumentRetentionScreen {...props} />}
      />
      <Route
        path="retention/documents/:documentId/:intent"
        element={<DocumentRetentionChangeScreen {...props} />}
      />
      <Route
        path="retention/documents/:documentId/revisions/:revisionId"
        element={<DocumentRetirementScreen {...props} />}
      />
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
      <Route
        path="*"
        element={
          <Navigate
            to={documentHref(
              props.access.companyId,
              isDocumentRetentionPath(location.pathname)
                ? documentRetentionPoliciesPath
                : undefined,
            )}
            replace
          />
        }
      />
    </Routes>
  );
}
