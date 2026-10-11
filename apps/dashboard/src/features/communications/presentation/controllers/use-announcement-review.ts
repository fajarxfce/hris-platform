import { useQuery } from "@tanstack/react-query";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { CommunicationsUseCases } from "../contracts/communications-use-cases";

export function useAnnouncementReview(
  account: AccountId,
  access: CompanyAccess,
  id: string,
  load: CommunicationsUseCases["loadReview"],
) {
  const query = useQuery({
    queryKey: ["announcement-publication", account, access.companyId, access.permissions, id],
    queryFn: ({ signal }) => load.execute(access, id, signal),
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
    refetchOnMount: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
  const result = query.isFetching ? null : query.isError ? failed("unexpected_error") : query.data;
  return {
    review: result?.ok ? result.value : null,
    failure: result && !result.ok ? result.failure : null,
    loading: query.isPending || query.isFetching,
    refresh: () => {
      void query.refetch();
    },
  };
}
