import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback, useMemo } from "react";
import type { AccountId } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpensePaymentReview } from "../../domain/entities/expense-payment-review";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
export function useExpensePayment(
  account: AccountId,
  access: CompanyAccess,
  id: string,
  after: string | null,
  load: ExpenseUseCases["loadPayment"],
) {
  const client = useQueryClient();
  const key = useMemo(
    () => ["expense-payment", account, access.companyId, access.permissions, id, after],
    [account, access.companyId, access.permissions, id, after],
  );
  const query = useQuery({
    queryKey: key,
    queryFn: ({ signal }) => load.execute(access, id, after, signal),
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
    structuralSharing: false,
    refetchOnMount: "always",
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
  const withdrawReview = useCallback(
    (observed: ExpensePaymentReview, failure: Failure) => {
      client.setQueryData<Result<ExpensePaymentReview>>(key, (current) =>
        current?.ok && current.value === observed ? { ok: false, failure } : current,
      );
    },
    [client, key],
  );
  const result = query.isFetching ? null : query.isError ? failed("unexpected_error") : query.data;
  return {
    loading: query.isPending || query.isFetching,
    review: result?.ok ? result.value : null,
    failure: result && !result.ok ? result.failure : null,
    withdrawReview,
    refresh: () => {
      void query.refetch();
    },
  };
}
