import { useQuery } from "@tanstack/react-query";
import { useReducer } from "react";
import { useController, useForm } from "react-hook-form";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPayableSearch } from "../../domain/entities/payroll-payable-search";
import { parsePayrollPaymentSearch } from "../../domain/policies/payroll-payment-read-policy";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
export function usePayrollPayablePicker(
  account: AccountId,
  access: CompanyAccess,
  initial: PayrollPayableSearch,
  load: PayrollUseCases["loadPayables"],
) {
  const [search, apply] = useReducer(
    (_previous: PayrollPayableSearch, next: PayrollPayableSearch) => next,
    initial,
  );
  const form = useForm({ defaultValues: { from: initial.from, until: initial.until } });
  const from = useController({ name: "from", control: form.control }),
    until = useController({ name: "until", control: form.control });
  const query = useQuery({
    queryKey: ["payroll-payables", account, access.companyId, access.permissions, search],
    queryFn: ({ signal }) => load.execute(access, search, signal),
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
    from: from.field,
    until: until.field,
    submit: form.handleSubmit((fields) => {
      const parsed = parsePayrollPaymentSearch({ ...fields, status: null, after: null });
      if (parsed.ok) apply({ from: parsed.value.from, until: parsed.value.until, after: null });
      else form.setError("root", { type: parsed.failure.code });
    }),
    filterFailure: form.formState.errors.root?.type
      ? { code: String(form.formState.errors.root.type), fields: {}, parameters: {} }
      : null,
    loading: query.isPending || query.isFetching,
    failure: result && !result.ok ? result.failure : null,
    page,
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
