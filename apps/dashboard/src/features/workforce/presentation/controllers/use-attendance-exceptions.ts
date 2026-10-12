import { useQuery } from "@tanstack/react-query";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AttendanceExceptionSearchInput } from "../../domain/entities/attendance-exception-search";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";

export function useAttendanceExceptions(
  account: AccountId,
  access: CompanyAccess,
  search: AttendanceExceptionSearchInput,
  load: WorkforceUseCases["loadAttendanceExceptions"],
) {
  const query = useQuery({
    queryKey: [
      "workforce-attendance-exceptions",
      account,
      access.companyId,
      access.permissions,
      search,
    ],
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
