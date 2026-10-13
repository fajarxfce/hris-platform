import { useQuery } from "@tanstack/react-query";
import { useReducer } from "react";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveUseCases } from "../contracts/leave-use-cases";

export function useLeaveRequestTypes(
  account: AccountId,
  access: CompanyAccess,
  asOf: string,
  load: LeaveUseCases["loadRequestTypes"],
) {
  const [after, apply] = useReducer((_old: string | null, next: string | null) => next, null);
  const query = useQuery({
    networkMode: "always",
    queryKey: ["leave-request-types", account, access.companyId, access.permissions, asOf, after],
    queryFn: ({ signal }) => load.execute(access, asOf, after, signal),
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
    structuralSharing: false,
    refetchOnMount: "always",
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
  const result = query.isFetching ? null : query.isError ? failed("unexpected_error") : query.data;
  const page = result?.ok ? result.value : null;
  return {
    page,
    loading: query.isPending || query.isFetching,
    failure: result && !result.ok ? result.failure : null,
    firstPage: after === null,
    first: () => apply(null),
    next: () => {
      if (page?.nextCursor) apply(page.nextCursor);
    },
    refresh: () => {
      void query.refetch();
    },
  };
}
