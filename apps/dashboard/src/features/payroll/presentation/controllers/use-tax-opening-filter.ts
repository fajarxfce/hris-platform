import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import {
  type TaxOpeningSearch,
  taxOpeningEmployeeStatuses,
} from "../../domain/entities/tax-opening-review";
export function useTaxOpeningFilter(
  search: TaxOpeningSearch,
  onApply: (value: TaxOpeningSearch) => void,
) {
  const values = useMemo(
    () => ({ year: search.year, query: search.query, status: search.status ?? "" }),
    [search.year, search.query, search.status],
  );
  const form = useForm({ values });
  const year = useController({ name: "year", control: form.control }).field;
  const query = useController({ name: "query", control: form.control }).field;
  const status = useController({ name: "status", control: form.control }).field;
  return {
    year,
    query,
    status,
    submit: form.handleSubmit((value) =>
      onApply({
        year: value.year,
        query: value.query.trim(),
        status: taxOpeningEmployeeStatuses.find((status) => status === value.status) ?? null,
        after: null,
      }),
    ),
  };
}
