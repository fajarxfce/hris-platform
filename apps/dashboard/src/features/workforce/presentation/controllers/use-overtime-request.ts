import { useQuery } from "@tanstack/react-query";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";

export function useOvertimeRequest(
  account: AccountId,
  access: CompanyAccess,
  id: string,
  after: string | null,
  load: WorkforceUseCases["loadOvertimeRequest"],
) {
  const query = useQuery({
    queryKey: [
      "workforce-overtime-request",
      account,
      access.companyId,
      access.permissions,
      id,
      after,
    ],
    queryFn: ({ signal }) => load.execute(access, id, after, signal),
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
    structuralSharing: false,
    refetchOnMount: "always",
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
  const result = query.isFetching ? null : query.isError ? failed("unexpected_error") : query.data;
  return {
    loading: query.isPending || query.isFetching,
    details: result?.ok ? result.value : null,
    failure: result && !result.ok ? result.failure : null,
    refresh: () => {
      void query.refetch();
    },
  };
}
