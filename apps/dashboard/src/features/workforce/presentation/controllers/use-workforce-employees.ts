import { useQuery } from "@tanstack/react-query";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { WorkforceEmployeeSearch } from "../../domain/entities/workforce-employee-search";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";

/** A company/account selection owns one page; obsolete reads are cancelled and discarded. */
export function useWorkforceEmployees(
  account: AccountId,
  access: CompanyAccess,
  search: WorkforceEmployeeSearch,
  load: WorkforceUseCases["loadWorkforceEmployees"],
) {
  const query = useQuery({
    queryKey: ["workforce-employees", account, access.companyId, access.permissions, search],
    queryFn: ({ signal }) => load.execute(access, search, signal),
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
    page: result?.ok ? result.value : null,
    failure: result && !result.ok ? result.failure : null,
    refresh: () => {
      void query.refetch();
    },
  };
}
