import { useQuery } from "@tanstack/react-query";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpensePaymentSearchInput } from "../../domain/entities/expense-payment-search";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
export function useExpensePayments(
  account: AccountId,
  access: CompanyAccess,
  search: ExpensePaymentSearchInput,
  load: ExpenseUseCases["loadPayments"],
) {
  const query = useQuery({
    queryKey: ["expense-payments", account, access.companyId, access.permissions, search],
    queryFn: ({ signal }) => load.execute(access, search, signal),
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
    page: result?.ok ? result.value : null,
    failure: result && !result.ok ? result.failure : null,
    refresh: () => {
      void query.refetch();
    },
  };
}
