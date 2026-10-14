import type { CompanyId } from "../../../../core/domain/identifiers";
import {
  type PayrollPublicationSearch,
  payrollPublicationStatuses,
} from "../../domain/entities/payroll-publication-search";
export function payrollPublicationSearchFromRoute(
  parameters: URLSearchParams,
  company: CompanyId,
  today: string,
): PayrollPublicationSearch {
  const replaced = parameters.has("company") && parameters.get("company") !== company;
  return {
    from: replaced
      ? `${today.slice(0, 4)}-01`
      : (parameters.get("from") ?? `${today.slice(0, 4)}-01`),
    until: replaced
      ? `${today.slice(0, 4)}-12`
      : (parameters.get("until") ?? `${today.slice(0, 4)}-12`),
    status: replaced
      ? null
      : (payrollPublicationStatuses.find((value) => value === parameters.get("status")) ?? null),
    after: replaced ? null : parameters.get("after"),
  };
}
export function payrollPublicationParameters(
  company: CompanyId,
  search: PayrollPublicationSearch,
): URLSearchParams {
  const params = new URLSearchParams({ company, from: search.from, until: search.until });
  if (search.status !== null) params.set("status", search.status);
  if (search.after !== null) params.set("after", search.after);
  return params;
}
export function payrollFinalizationsPath(company: string, run: string): string {
  return `/payroll/runs/${encodeURIComponent(run)}/finalizations?${new URLSearchParams({ company })}`;
}
export function payrollFinalizationStartPath(company: string, run: string): string {
  return `/payroll/runs/${encodeURIComponent(run)}/finalize?${new URLSearchParams({ company })}`;
}
export function payrollFinalizationPath(company: string, id: string): string {
  return `/payroll/finalizations/${encodeURIComponent(id)}?${new URLSearchParams({ company })}`;
}
