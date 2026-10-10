import { useQuery } from "@tanstack/react-query";
import { useReducer } from "react";
import { useController, useForm } from "react-hook-form";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LoadOrganizationUnits } from "../../../organization/domain/usecases/load-organization-units";
import { isWorkingOn } from "../../domain/policies/employee-policy";
import type { LoadEmployees } from "../../domain/usecases/load-employees";
import type {
  EmployeeAssignmentKind,
  EmployeeAssignmentOption,
} from "../models/employee-assignment";

type Options = Readonly<{ items: readonly EmployeeAssignmentOption[]; nextCursor: string | null }>;
type Search = Readonly<{ query: string; after: string | null }>;

/** A picker owns one bounded page; an abandoned query is cancelled, not cached or retried. */
export function useEmployeeAssignmentPicker(
  accountId: AccountId,
  access: CompanyAccess,
  kind: EmployeeAssignmentKind,
  startDate: string,
  loadUnits: Pick<LoadOrganizationUnits, "execute">,
  loadEmployees: Pick<LoadEmployees, "execute">,
) {
  const [search, apply] = useReducer((_old: Search, next: Search) => next, {
    query: "",
    after: null,
  });
  const form = useForm({ defaultValues: { query: "" } });
  const queryField = useController({ name: "query", control: form.control });
  const query = useQuery({
    queryKey: [
      "employee-assignment",
      accountId,
      access.companyId,
      access.permissions,
      kind,
      kind === "MANAGER" ? startDate : null,
      search,
    ],
    queryFn: async ({ signal }): Promise<Result<Options>> => {
      if (kind === "MANAGER") {
        const result = await loadEmployees.execute(access, { ...search, asOf: startDate }, signal);
        if (!result.ok) return result;
        return success({
          items: result.value.items
            .filter((employee) => isWorkingOn(employee.terms, startDate))
            .map((employee) => ({
              id: employee.id,
              label: `${employee.employeeNumber} · ${employee.legalName}`,
              cells: [employee.employeeNumber, employee.legalName],
            })),
          nextCursor: result.value.nextCursor,
        });
      }
      const result = await loadUnits.execute(access, { ...search, kind, active: "true" }, signal);
      if (!result.ok) return result;
      return success({
        items: result.value.items.map((unit) => ({
          id: unit.id,
          label: `${unit.code} · ${unit.name}`,
          cells: [unit.code, unit.name],
        })),
        nextCursor: result.value.nextCursor,
      });
    },
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
    query: queryField.field,
    apply: form.handleSubmit((fields) => apply({ query: fields.query.trim(), after: null })),
    loading: query.isPending || query.isFetching,
    failure: result && !result.ok ? result.failure : null,
    options: page?.items ?? [],
    nextCursor: page?.nextCursor ?? null,
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
