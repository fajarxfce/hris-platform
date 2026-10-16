import { useReducer } from "react";
import { useController, useForm } from "react-hook-form";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import { useWorkforceEmployees } from "./use-workforce-employees";

export function useRosterBulkEmployees(
  account: AccountId,
  access: CompanyAccess,
  asOf: string,
  load: WorkforceUseCases["loadWorkforceEmployees"],
) {
  const [search, setSearch] = useReducer(
    (
      _old: { query: string; after: string | null },
      next: { query: string; after: string | null },
    ) => next,
    { query: "", after: null },
  );
  const form = useForm({ defaultValues: { query: "" } });
  const query = useController({ control: form.control, name: "query" });
  const state = useWorkforceEmployees(account, access, { ...search, asOf }, load);
  return {
    ...state,
    query: query.field,
    firstPage: search.after === null,
    search: form.handleSubmit((fields) => setSearch({ query: fields.query.trim(), after: null })),
    first: () => setSearch({ ...search, after: null }),
    next: () => {
      if (state.page?.nextCursor) setSearch({ ...search, after: state.page.nextCursor });
    },
  };
}
