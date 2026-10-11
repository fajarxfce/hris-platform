import { useQuery } from "@tanstack/react-query";
import { type KeyboardEvent, useReducer } from "react";
import { useController, useForm } from "react-hook-form";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AudienceReferenceKind } from "../../domain/entities/audience-reference";
import type { LoadAudienceReferences } from "../../domain/usecases/load-audience-references";

type Search = Readonly<{ query: string; after: string | null }>;

export function useAudienceReferencePicker(
  account: AccountId,
  access: CompanyAccess,
  kind: AudienceReferenceKind,
  load: Pick<LoadAudienceReferences, "execute">,
  enabled: boolean,
) {
  const [search, apply] = useReducer((_previous: Search, next: Search) => next, {
    query: "",
    after: null,
  });
  const form = useForm({ defaultValues: { query: "" } });
  const queryField = useController({ name: "query", control: form.control });
  const query = useQuery({
    queryKey: [
      "audience-reference-search",
      account,
      access.companyId,
      access.permissions,
      kind,
      search,
    ],
    queryFn: ({ signal }) => load.execute(access, { ...search, kind, ids: [] }, signal),
    enabled,
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
    refetchOnMount: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
  const result =
    !enabled || query.isFetching ? null : query.isError ? failed("unexpected_error") : query.data;
  const page = result?.ok ? result.value : null;
  const submit = form.handleSubmit((input) => apply({ query: input.query.trim(), after: null }));
  return {
    query: queryField.field,
    search: () => {
      if (enabled) void submit();
    },
    onQueryKeyDown: (event: KeyboardEvent<HTMLInputElement>) => {
      if (event.key === "Enter") {
        event.preventDefault();
        if (enabled) void submit();
      }
    },
    loading: enabled && (query.isPending || query.isFetching),
    failure: result && !result.ok ? result.failure : null,
    page,
    firstPage: search.after === null,
    first: () => {
      if (enabled) apply({ ...search, after: null });
    },
    next: () => {
      if (enabled && page?.nextCursor) apply({ ...search, after: page.nextCursor });
    },
    refresh: () => {
      if (enabled) void query.refetch();
    },
  };
}
