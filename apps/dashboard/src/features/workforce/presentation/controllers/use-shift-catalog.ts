import { useQuery } from "@tanstack/react-query";
import { useReducer } from "react";
import { useController, useForm } from "react-hook-form";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ShiftSearch } from "../../domain/entities/shift-search";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";

/** Retains one catalog page. Selected snapshots belong to the editing form, not this query. */
export function useShiftCatalog(
  account: AccountId,
  access: CompanyAccess,
  load: WorkforceUseCases["loadShifts"],
  enabled: boolean,
) {
  const [search, apply] = useReducer((_old: ShiftSearch, next: ShiftSearch) => next, {
    query: "",
    after: null,
  });
  const form = useForm({ defaultValues: { query: "" } });
  const field = useController({ name: "query", control: form.control });
  const query = useQuery({
    queryKey: ["calendar-shift-catalog", account, access.companyId, access.permissions, search],
    queryFn: ({ signal }) => load.execute(access, search, signal),
    enabled,
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
    refetchOnMount: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
  const result = query.isFetching ? null : query.isError ? failed("unexpected_error") : query.data;
  const page = result?.ok ? result.value : null;
  return {
    query: field.field,
    apply: form.handleSubmit((values) => apply({ query: values.query.trim(), after: null })),
    loading: enabled && (query.isPending || query.isFetching),
    failure: result && !result.ok ? result.failure : null,
    shifts: page?.items ?? [],
    hasActiveShifts: page?.items.some((shift) => shift.active) ?? false,
    firstPage: search.after === null,
    nextCursor: page?.nextCursor ?? null,
    first: () => apply({ ...search, after: null }),
    next: () => {
      if (page?.nextCursor) apply({ ...search, after: page.nextCursor });
    },
    refresh: () => {
      if (enabled) void query.refetch();
    },
  };
}
