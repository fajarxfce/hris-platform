import { useQuery } from "@tanstack/react-query";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { CommunicationsUseCases } from "../contracts/communications-use-cases";

export function useAudienceGroups(
  account: AccountId,
  access: CompanyAccess,
  after: string | null,
  load: CommunicationsUseCases["loadGroups"],
) {
  const query = useQuery({
    queryKey: ["audience-groups", account, access.companyId, access.permissions, after],
    queryFn: ({ signal }) => load.execute(access, after, signal),
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
    refetchOnMount: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
  const result = query.isFetching ? null : query.isError ? failed("unexpected_error") : query.data;
  return {
    page: result?.ok ? result.value : null,
    failure: result && !result.ok ? result.failure : null,
    loading: query.isPending || query.isFetching,
    refresh: () => {
      void query.refetch();
    },
  };
}
