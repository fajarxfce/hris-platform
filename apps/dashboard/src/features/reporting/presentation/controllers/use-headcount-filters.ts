import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyMembership } from "../../../identity/domain/entities/session";
import { isHeadcountCompanies, isHeadcountDate } from "../../domain/policies/headcount-policy";
import { reportingMessages } from "../i18n/reporting-messages";

/** Form mechanics keep partial edits separate from the applied URL filter. */
export function useHeadcountFilters(
  asOf: string,
  selected: readonly CompanyId[],
  available: readonly CompanyMembership[],
  locale: Locale,
  apply: (date: string, companies: readonly CompanyId[]) => void,
) {
  const text = reportingMessages(locale);
  const options = useMemo(
    () =>
      available.map((company) => ({
        value: company.id,
        label: `${company.name} (${company.code})`,
      })),
    [available],
  );
  const values = useMemo(
    () => ({ asOf: isHeadcountDate(asOf) ? asOf : "", companies: [...selected] }),
    [asOf, selected],
  );
  const form = useForm<{ asOf: string; companies: CompanyId[] }>({ values });
  const input = useController({
    name: "asOf",
    control: form.control,
    rules: { required: true, validate: isHeadcountDate },
  });
  const companies = useController({
    name: "companies",
    control: form.control,
    rules: {
      validate: (ids) =>
        isHeadcountCompanies(ids) &&
        ids.every((id) => available.some((company) => company.id === id)),
    },
  });
  return {
    date: input.field,
    error: input.fieldState.error
      ? failureMessage({ code: "invalid_report_date", fields: {}, parameters: {} }, locale)
      : undefined,
    companies: {
      ref: companies.field.ref,
      value: companies.field.value,
      options,
      onChange: (ids: string[]) => companies.field.onChange(ids),
      displayValue:
        companies.field.value.length > 1
          ? `${new Intl.NumberFormat(locale).format(companies.field.value.length)} ${text.selectedCompanies}`
          : (options.find((option) => option.value === companies.field.value[0])?.label ?? ""),
      error: companies.fieldState.error
        ? failureMessage({ code: "invalid_report_companies", fields: {}, parameters: {} }, locale)
        : undefined,
    },
    apply: form.handleSubmit((input) => apply(input.asOf, input.companies)),
  };
}
