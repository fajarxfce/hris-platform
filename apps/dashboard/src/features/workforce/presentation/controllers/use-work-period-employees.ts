import { useQuery } from "@tanstack/react-query";
import { useReducer } from "react";
import { useController, useForm } from "react-hook-form";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";

type Search = Readonly<{ query: string; after: string | null }>;
export function useWorkPeriodEmployees(
  account: AccountId,
  access: CompanyAccess,
  month: string,
  jobId: string,
  load: WorkforceUseCases["loadWorkPeriodEmployees"],
) {
  const [search, apply] = useReducer((_old: Search, next: Search) => next, {
    query: "",
    after: null,
  });
  const form = useForm({ defaultValues: { query: "" } });
  const field = useController({ name: "query", control: form.control });
  const request = useQuery({
    queryKey: [
      "work-period-employees",
      account,
      access.companyId,
      access.permissions,
      month,
      jobId,
      search,
    ],
    queryFn: ({ signal }) => load.execute(access, { month, ...search }, signal),
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
    structuralSharing: false,
    refetchOnMount: "always",
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
    page,
    firstPage: search.after === null,
    first: () => apply({ ...search, after: null }),
    next: () => {
      if (page?.nextCursor) apply({ ...search, after: page.nextCursor });
    },
    refresh: () => {
      void request.refetch();
    },
  };
}
