import { useQuery } from "@tanstack/react-query";
import { useReducer } from "react";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AudienceReferenceKind } from "../../domain/entities/audience-reference";
import type { LoadAudienceReferences } from "../../domain/usecases/load-audience-references";

export function useSelectedAudienceReferences(
  account: AccountId,
  access: CompanyAccess,
  kind: AudienceReferenceKind,
  ids: readonly string[],
  load: Pick<LoadAudienceReferences, "execute">,
) {
  const [requestedPage, setPage] = useReducer((_previous: number, next: number) => next, 0);
  const lastPage = Math.max(0, Math.ceil(ids.length / 50) - 1);
  const page = Math.min(requestedPage, lastPage);
  const selected = ids.slice(page * 50, (page + 1) * 50);
  const query = useQuery({
    queryKey: [
      "audience-selected-references",
      account,
      access.companyId,
      access.permissions,
      kind,
      selected,
    ],
    queryFn: ({ signal }) =>
      load.execute(access, { kind, ids: selected, query: "", after: null }, signal),
    enabled: selected.length > 0,
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
    refetchOnMount: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
  const result =
    selected.length === 0 || query.isFetching
      ? null
      : query.isError
        ? failed("unexpected_error")
        : query.data;
  return {
    ids: selected,
    page,
    lastPage,
    references: result?.ok ? result.value.items : [],
    loading: selected.length > 0 && (query.isPending || query.isFetching),
    failure: result && !result.ok ? result.failure : null,
    first: () => setPage(0),
    previous: () => setPage(Math.max(0, page - 1)),
    next: () => setPage(Math.min(lastPage, page + 1)),
    refresh: () => {
      if (selected.length > 0) void query.refetch();
    },
  };
}
