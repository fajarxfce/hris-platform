import { useQuery } from "@tanstack/react-query";
import { useReducer } from "react";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../domain/entities/session";
import type { LoadRoleTemplates } from "../../domain/usecases/load-role-templates";

/** The mounted picker owns one page; closing removes its query and cancels acquisition. */
export function useCompanyRolePicker(
  accountId: AccountId,
  access: CompanyAccess,
  load: Pick<LoadRoleTemplates, "execute">,
) {
  const [after, setAfter] = useReducer(
    (_previous: string | null, next: string | null) => next,
    null,
  );
  const query = useQuery({
    queryKey: ["company-member-role", accountId, access.companyId, access.permissions, after],
    queryFn: ({ signal }) => load.execute(access, after, signal),
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
    refetchOnMount: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
  const result = query.isFetching ? null : query.isError ? failed("unexpected_error") : query.data;
  const page = result?.ok ? result.value : null;
  return {
    loading: query.isPending || query.isFetching,
    failure: result && !result.ok ? result.failure : null,
    items: page?.items ?? [],
    nextCursor: page?.nextCursor ?? null,
    firstPage: after === null,
    first: () => setAfter(null),
    next: () => {
      if (page?.nextCursor) setAfter(page.nextCursor);
    },
    refresh: () => {
      void query.refetch();
    },
  };
}
