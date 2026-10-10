import { useQuery } from "@tanstack/react-query";
import { useReducer } from "react";
import { useController, useForm } from "react-hook-form";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";

type Search = Readonly<{ query: string; after: string | null }>;
export function useLifecycleAssigneePicker(
  account: AccountId,
  access: CompanyAccess,
  load: LifecycleUseCases["loadAssignees"],
  excluded: AccountId | null,
) {
  const [search, apply] = useReducer((_old: Search, next: Search) => next, {
    query: "",
    after: null,
  });
  const form = useForm({ defaultValues: { query: "" } });
  const field = useController({ name: "query", control: form.control });
  const request = useQuery({
    queryKey: ["lifecycle-assignees", account, access.companyId, access.permissions, search],
    queryFn: ({ signal }) => load.execute(access, search.query, search.after, signal),
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
    query: field.field,
    apply: form.handleSubmit((fields) => {
      const query = fields.query.trim();
      if (query === search.query && search.after === null) void request.refetch();
      else apply({ query, after: null });
    }),
    loading: request.isPending || request.isFetching,
    failure: result && !result.ok ? result.failure : null,
    options: page?.items.filter((item) => item.id !== excluded) ?? [],
    firstPage: search.after === null,
    nextCursor: page?.nextCursor ?? null,
    first: () => apply({ ...search, after: null }),
    next: () => {
      if (page?.nextCursor) apply({ ...search, after: page.nextCursor });
    },
    refresh: () => {
      void request.refetch();
    },
  };
}
