import { useQuery } from "@tanstack/react-query";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";

export function useAttendanceCorrectionHistory(
  account: AccountId,
  access: CompanyAccess,
  employee: string,
  workDate: string,
  after: string | null,
  load: WorkforceUseCases["loadAttendanceCorrectionHistory"],
) {
  const query = useQuery({
    queryKey: [
      "workforce-attendance-corrections",
      account,
      access.companyId,
      access.permissions,
      employee,
      workDate,
      after,
    ],
    queryFn: ({ signal }) => load.execute(access, employee, workDate, after, signal),
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
    history: result?.ok ? result.value : null,
    failure: result && !result.ok ? result.failure : null,
    refresh: () => {
      void query.refetch();
    },
  };
}
