import { useMemo } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { DocumentsScreenProps } from "../contracts/documents-screen-props";
import { useDocumentEmployeeFilter } from "../controllers/use-document-employee-filter";
import { useDocumentEmployees } from "../controllers/use-document-reads";
import { documentFolderPath, documentHref, documentParameters } from "../models/document-route";
import { documentEmployeeRows } from "../models/document-view";
import { DocumentEmployeesPage } from "../pages/document-employees-page";

export function DocumentEmployeesScreen(props: DocumentsScreenProps) {
  const [parameters, setParameters] = useSearchParams(),
    navigate = useNavigate();
  const search = useMemo(
    () => ({ query: parameters.get("query") ?? "", after: parameters.get("after") }),
    [parameters],
  );
  const state = useDocumentEmployees(props, search);
  const filters = useDocumentEmployeeFilter(search, (query) =>
    setParameters(documentParameters(props.access.companyId, null, query)),
  );
  const rows = useMemo(
    () => (state.data ? documentEmployeeRows(state.data, props.locale) : []),
    [state.data, props.locale],
  );
  useWorkspaceRevalidation(state.failure);
  return (
    <DocumentEmployeesPage
      {...props}
      state={state}
      filters={filters}
      rows={rows}
      firstPage={search.after === null}
      onFirst={() => setParameters(documentParameters(props.access.companyId, null, search.query))}
      onNext={() => {
        if (state.data?.employees.nextCursor)
          setParameters(
            documentParameters(
              props.access.companyId,
              state.data.employees.nextCursor,
              search.query,
            ),
          );
      }}
      onOpen={(id) => navigate(documentHref(props.access.companyId, documentFolderPath(id)))}
    />
  );
}
