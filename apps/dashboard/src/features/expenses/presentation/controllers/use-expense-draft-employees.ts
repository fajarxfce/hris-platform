import { useQuery } from "@tanstack/react-query";
import { useReducer } from "react";
import { useController, useForm } from "react-hook-form";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";

type Search = Readonly<{ query: string; after: string | null }>;
export function useExpenseDraftEmployees(
  account: AccountId,
  access: CompanyAccess,
  load: ExpenseUseCases["loadDraftEmployees"],
) {
  const [search, apply] = useReducer((_previous: Search, next: Search) => next, {
    query: "",
    after: null,
  });
  const form = useForm({ defaultValues: { query: "" } });
  const input = useController({ name: "query", control: form.control, rules: { maxLength: 120 } });
  const query = useQuery({
    queryKey: ["expense-draft-employees", account, access.companyId, access.permissions, search],
    queryFn: ({ signal }) => load.execute(access, search.query, search.after, signal),
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
    structuralSharing: false,
    refetchOnMount: "always",
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
  const result = query.isFetching ? null : query.isError ? failed("unexpected_error") : query.data,
    directory = result?.ok ? result.value : null;
  return {
    input: input.field,
    directory,
    loading: query.isPending || query.isFetching,
    failure: result && !result.ok ? result.failure : null,
    submit: form.handleSubmit((fields) => {
      if (search.query === fields.query.trim() && search.after === null) void query.refetch();
      else apply({ query: fields.query.trim(), after: null });
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
