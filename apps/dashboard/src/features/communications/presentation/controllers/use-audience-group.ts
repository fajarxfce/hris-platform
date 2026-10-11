import { useQuery } from "@tanstack/react-query";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { CommunicationsUseCases } from "../contracts/communications-use-cases";

export function useAudienceGroup(
  account: AccountId,
  access: CompanyAccess,
  id: string,
  revision: string | null,
  load: CommunicationsUseCases["loadGroup"],
) {
  const query = useQuery({
    queryKey: ["audience-group", account, access.companyId, access.permissions, id, revision],
    queryFn: ({ signal }) => load.execute(access, id, revision, signal),
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
    refetchOnMount: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
  const result = query.isFetching ? null : query.isError ? failed("unexpected_error") : query.data;
  return {
    group: result?.ok ? result.value : null,
    failure: result && !result.ok ? result.failure : null,
    loading: query.isPending || query.isFetching,
    refresh: () => {
      void query.refetch();
    },
  };
}
