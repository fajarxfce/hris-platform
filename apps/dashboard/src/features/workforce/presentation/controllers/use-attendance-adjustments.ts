import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo } from "react";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";

export function useAttendanceAdjustments(
  account: AccountId,
  access: CompanyAccess,
  employee: string,
  month: string,
  before: string | null,
  visible: boolean,
  load: WorkforceUseCases["loadAttendanceAdjustments"],
) {
  const client = useQueryClient();
  const queryKey = useMemo(
    () => [
      "loadAttendanceAdjustments",
      account,
      access.companyId,
      access.permissions,
      employee,
      month,
      before,
    ],
    [account, access, employee, month, before],
  );
  const query = useQuery({
    queryKey,
    queryFn: ({ signal }) => load.execute(access, employee, month, before, signal),
    enabled: false,
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
    structuralSharing: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
  const { refetch } = query;
  useEffect(() => {
    if (!visible) return;
    void refetch();
    return () => {
      void client.cancelQueries({ queryKey, exact: true });
    };
  }, [visible, client, queryKey, refetch]);
  const result =
    !visible || query.isFetching ? null : query.isError ? failed("unexpected_error") : query.data;
  return {
    loading: visible && (query.isPending || query.isFetching),
    history: result?.ok ? result.value : null,
    failure: result && !result.ok ? result.failure : null,
    refresh: () => {
      if (visible && !query.isFetching) void refetch();
    },
  };
}
