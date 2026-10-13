import { useQuery } from "@tanstack/react-query";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
export function useExpenseClaim(
  account: AccountId,
  access: CompanyAccess,
  id: string,
  historyAfter: string | null,
  load: ExpenseUseCases["loadClaim"],
) {
  const query = useQuery({
    queryKey: ["expense-claim", account, access.companyId, access.permissions, id, historyAfter],
    queryFn: ({ signal }) => load.execute(access, id, historyAfter, signal),
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
    review: result?.ok ? result.value : null,
    failure: result && !result.ok ? result.failure : null,
    refresh: () => {
      void query.refetch();
    },
  };
}
