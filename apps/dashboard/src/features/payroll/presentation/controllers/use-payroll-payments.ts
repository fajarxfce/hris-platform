import { useQuery } from "@tanstack/react-query";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPaymentSearchInput } from "../../domain/entities/payroll-payment-search";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
export function usePayrollPayments(
  account: AccountId,
  access: CompanyAccess,
  search: PayrollPaymentSearchInput,
  load: PayrollUseCases["loadPayments"],
) {
  const query = useQuery({
    queryKey: ["payroll-payments", account, access.companyId, access.permissions, search],
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
