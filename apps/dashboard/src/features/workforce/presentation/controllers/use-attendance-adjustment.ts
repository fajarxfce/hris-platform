import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo } from "react";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";

export function useAttendanceAdjustment(
  account: AccountId,
  access: CompanyAccess,
  employee: string,
  month: string,
  id: string,
  visible: boolean,
  load: WorkforceUseCases["loadAttendanceAdjustment"],
) {
  const client = useQueryClient();
  const queryKey = useMemo(
    () => [
      "loadAttendanceAdjustment",
      account,
      access.companyId,
      access.permissions,
      employee,
      month,
      id,
    ],
    [account, access, employee, month, id],
  );
  const query = useQuery({
    queryKey,
    queryFn: ({ signal }) => load.execute(access, employee, month, id, signal),
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
    details: result?.ok ? result.value : null,
    failure: result && !result.ok ? result.failure : null,
    refresh: () => {
      if (visible && !query.isFetching) void refetch();
    },
  };
}
