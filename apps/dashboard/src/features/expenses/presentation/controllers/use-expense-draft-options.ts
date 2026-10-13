import { useQuery } from "@tanstack/react-query";
import { useReducer } from "react";
import { useController, useForm } from "react-hook-form";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import type { ExpenseDraftOption, ExpenseDraftOptionPage } from "../models/expense-draft-option";

type Search = Readonly<{ query: string; after: string | null }>;
export function useExpenseDraftOptions(
  account: AccountId,
  access: CompanyAccess,
  actions: Pick<ExpenseUseCases, "loadCategories" | "loadCostCenters" | "loadDraftReceipts">,
  kind: ExpenseDraftOption["kind"],
  employment: string,
  occurredOn: string,
) {
  const [search, apply] = useReducer((_previous: Search, next: Search) => next, {
    query: "",
    after: null,
  });
  const form = useForm({ defaultValues: { query: "" } });
  const input = useController({ name: "query", control: form.control, rules: { maxLength: 120 } });
  const query = useQuery({
    queryKey: [
      "expense-draft-options",
      account,
      access.companyId,
      access.permissions,
      kind,
      employment,
      occurredOn,
      search,
    ],
    queryFn: async ({ signal }): Promise<Result<ExpenseDraftOptionPage>> => {
      if (kind === "category") {
        const result = await actions.loadCategories.execute(
          access,
          occurredOn,
          search.after,
          signal,
        );
        return result.ok
          ? success({
              ...result.value,
              items: result.value.items.map((value) => ({ kind, id: value.id, value })),
            })
          : result;
      }
      if (kind === "costCenter") {
        const result = await actions.loadCostCenters.execute(
          access,
          search.query,
          search.after,
          signal,
        );
        return result.ok
          ? success({
              ...result.value,
              items: result.value.items.map((value) => ({ kind, id: value.id, value })),
            })
          : result;
      }
      const result = await actions.loadDraftReceipts.execute(
        access,
        employment,
        search.after,
        signal,
      );
      return result.ok
        ? success({
            ...result.value,
            items: result.value.items.map((value) => ({ kind, id: value.revisionId, value })),
          })
        : result;
    },
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
    structuralSharing: false,
    refetchOnMount: "always",
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
  const result = query.isFetching ? null : query.isError ? failed("unexpected_error") : query.data,
    page = result?.ok ? result.value : null;
  return {
    input: input.field,
    page,
    loading: query.isPending || query.isFetching,
    failure: result && !result.ok ? result.failure : null,
    submit: form.handleSubmit((fields) => {
      if (search.query === fields.query.trim() && search.after === null) void query.refetch();
      else apply({ query: fields.query.trim(), after: null });
    }),
    firstPage: search.after === null,
    first: () => apply({ ...search, after: null }),
    next: () => {
      if (page?.nextCursor) apply({ ...search, after: page.nextCursor });
    },
    refresh: () => {
      void query.refetch();
    },
  };
}
