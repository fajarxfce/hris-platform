import { useQuery } from "@tanstack/react-query";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { CalendarRange } from "../../domain/entities/calendar-range";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";

export function useEmployeeAttendance(
  account: AccountId,
  access: CompanyAccess,
  employee: string,
  range: CalendarRange,
  load: WorkforceUseCases["loadEmployeeAttendance"],
) {
  const query = useQuery({
    queryKey: [
      "workforce-attendance",
      account,
      access.companyId,
      access.permissions,
      employee,
      range,
    ],
    queryFn: ({ signal }) => load.execute(access, employee, range, signal),
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
    refetchOnMount: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
  const result = query.isFetching ? null : query.isError ? failed("unexpected_error") : query.data;
  return {
    loading: query.isPending || query.isFetching,
    attendance: result?.ok ? result.value : null,
    failure: result && !result.ok ? result.failure : null,
    refresh: () => {
      void query.refetch();
    },
  };
}
