import { useQuery } from "@tanstack/react-query";
import { useReducer } from "react";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveUseCases } from "../contracts/leave-use-cases";

export function useLeavePreparationEvidence(
  account: AccountId,
  access: CompanyAccess,
  employeeId: string,
  load: LeaveUseCases["loadPreparationEvidence"],
) {
  const [after, apply] = useReducer((_old: string | null, next: string | null) => next, null);
  const query = useQuery({
    networkMode: "always",
    queryKey: [
      "leave-preparation-evidence",
      account,
      access.companyId,
      access.permissions,
      employeeId,
      after,
    ],
    queryFn: ({ signal }) => load.execute(access, employeeId, after, signal),
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
