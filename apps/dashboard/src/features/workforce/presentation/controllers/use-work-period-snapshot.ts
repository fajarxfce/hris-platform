import { useQuery } from "@tanstack/react-query";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";

export function useWorkPeriodSnapshot(
  account: AccountId,
  access: CompanyAccess,
  month: string,
  employee: string,
  load: WorkforceUseCases["loadWorkPeriodSnapshot"],
) {
  const query = useQuery({
    queryKey: [
      "work-period-snapshot",
      account,
      access.companyId,
      access.permissions,
      month,
      employee,
    ],
    queryFn: ({ signal }) => load.execute(access, month, employee, signal),
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
    snapshot: result?.ok ? result.value : null,
    failure: result && !result.ok ? result.failure : null,
    refresh: () => {
      void query.refetch();
    },
  };
}
