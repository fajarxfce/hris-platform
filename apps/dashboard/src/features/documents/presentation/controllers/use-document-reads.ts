import { type QueryKey, useQuery } from "@tanstack/react-query";
import { failed, type Result } from "../../../../core/domain/result";
import type { DocumentEmployeeSearch } from "../../domain/entities/document-employee";
import type { DocumentsScreenProps } from "../contracts/documents-screen-props";
import type { DocumentReadState } from "../models/document-read-state";

/** Read ownership ends with the route. Refresh hides the prior authorized projection. */
function useDocumentRead<T>(
  scope: DocumentsScreenProps,
  key: QueryKey,
  read: (signal: AbortSignal) => Promise<Result<T>>,
): DocumentReadState<T> {
  const query = useQuery({
    queryKey: [
      "documents",
      scope.accountId,
      scope.access.companyId,
      scope.access.permissions,
      ...key,
    ],
    queryFn: ({ signal }) => read(signal),
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
    refetchOnMount: "always",
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    structuralSharing: false,
  });
  const result = query.isFetching ? null : query.isError ? failed("unexpected_error") : query.data;
  return {
    loading: query.isPending || query.isFetching,
    data: result?.ok ? result.value : null,
    failure: result && !result.ok ? result.failure : null,
    refresh: () => {
      void query.refetch();
    },
  };
}

export function useDocumentEmployees(props: DocumentsScreenProps, search: DocumentEmployeeSearch) {
  return useDocumentRead(props, ["employees", search], (signal) =>
    props.documents.loadEmployees.execute(props.access, search, signal),
  );
}
export function useDocumentFolder(
  props: DocumentsScreenProps,
  employee: string,
  after: string | null,
) {
  return useDocumentRead(props, ["folder", employee, after], (signal) =>
    props.documents.loadFolder.execute(props.access, employee, after, signal),
  );
}
export function useDocumentReview(
  props: DocumentsScreenProps,
  document: string,
  after: string | null,
) {
  return useDocumentRead(props, ["review", document, after], (signal) =>
    props.documents.loadReview.execute(props.access, document, after, signal),
  );
}
export function useDocumentRevision(
  props: DocumentsScreenProps,
  document: string,
  revision: string,
) {
  return useDocumentRead(props, ["revision", document, revision], (signal) =>
    props.documents.loadRevision.execute(props.access, document, revision, signal),
  );
}

export function useDocumentRetentionPolicies(props: DocumentsScreenProps) {
  return useDocumentRead(props, ["retention-policies"], (signal) =>
    props.documents.retention.loadPolicies.execute(props.access, signal),
  );
}
export function useDocumentRetentionPolicy(
  props: DocumentsScreenProps,
  classification: string,
  after: string | null,
) {
  return useDocumentRead(props, ["retention-policy", classification, after], (signal) =>
    props.documents.retention.loadPolicy.execute(props.access, classification, after, signal),
  );
}
