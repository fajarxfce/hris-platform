import { useQuery } from "@tanstack/react-query";
import { useReducer } from "react";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";

export function useLifecycleTemplatePicker(
  account: AccountId,
  access: CompanyAccess,
  load: LifecycleUseCases["loadTemplates"],
) {
  const [after, goTo] = useReducer((_old: string | null, next: string | null) => next, null);
  const request = useQuery({
    queryKey: ["lifecycle-template-picker", account, access.companyId, access.permissions, after],
    queryFn: ({ signal }) => load.execute(access, after, signal),
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
    refetchOnMount: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
  const result = request.isFetching
    ? null
    : request.isError
      ? failed("unexpected_error")
      : request.data;
  const page = result?.ok ? result.value : null;
  return {
    loading: request.isPending || request.isFetching,
    failure: result && !result.ok ? result.failure : null,
    options: page?.items.filter((item) => item.active) ?? [],
    firstPage: after === null,
    nextCursor: page?.nextCursor ?? null,
    first: () => goTo(null),
    next: () => {
      if (page?.nextCursor) goTo(page.nextCursor);
    },
    refresh: () => {
      void request.refetch();
    },
  };
}
