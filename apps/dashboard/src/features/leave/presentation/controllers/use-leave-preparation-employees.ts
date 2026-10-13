import { useQuery } from "@tanstack/react-query";
import { useReducer } from "react";
import { useController, useForm } from "react-hook-form";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveUseCases } from "../contracts/leave-use-cases";

type Search = Readonly<{ query: string; after: string | null }>;
export function useLeavePreparationEmployees(
  account: AccountId,
  access: CompanyAccess,
  load: LeaveUseCases["loadPreparationEmployees"],
) {
  const [search, apply] = useReducer((_old: Search, next: Search) => next, {
    query: "",
    after: null,
  });
  const form = useForm({ defaultValues: { query: "" } });
  const field = useController({ name: "query", control: form.control, rules: { maxLength: 120 } });
  const query = useQuery({
    networkMode: "always",
    queryKey: [
      "leave-preparation-employees",
      account,
      access.companyId,
      access.permissions,
      search,
    ],
    queryFn: ({ signal }) => load.execute(access, search.query, search.after, signal),
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
    structuralSharing: false,
    refetchOnMount: "always",
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
  const result = query.isFetching ? null : query.isError ? failed("unexpected_error") : query.data;
  const directory = result?.ok ? result.value : null;
  return {
    input: field.field,
    directory,
    loading: query.isPending || query.isFetching,
    failure: result && !result.ok ? result.failure : null,
    submit: form.handleSubmit((values) => {
      const input = values.query.trim();
      if (input === search.query && search.after === null) void query.refetch();
      else apply({ query: input, after: null });
    }),
    firstPage: search.after === null,
    first: () => apply({ ...search, after: null }),
    next: () => {
      if (directory?.employees.nextCursor)
        apply({ ...search, after: directory.employees.nextCursor });
    },
    refresh: () => {
      void query.refetch();
    },
  };
}
