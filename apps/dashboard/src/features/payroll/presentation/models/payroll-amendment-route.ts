import type { CompanyId } from "../../../../core/domain/identifiers";
import type { PayrollAmendmentStatus } from "../../domain/entities/payroll-amendment";
import type {
  PayrollAmendmentSearch,
  PayrollAmendmentSelection,
} from "../../domain/entities/payroll-amendment-search";
export function payrollAmendmentSearchFromRoute(
  parameters: URLSearchParams,
  company: CompanyId,
  year: number,
): PayrollAmendmentSearch {
  if (parameters.has("company") && parameters.get("company") !== company)
    return { year, status: null, query: "", employeeId: null, after: null };
  const raw = parameters.get("year") ?? String(year),
    duplicate = ["year", "status", "query", "employeeId", "after"].some(
      (key) => parameters.getAll(key).length > 1,
    );
  return {
    year: !duplicate && /^\d{4}$/u.test(raw) ? Number(raw) : Number.NaN,
    // Unknown route values remain invalid until the domain search policy checks them.
    status: parameters.get("status") as PayrollAmendmentStatus | null,
    query: parameters.get("query") ?? "",
    employeeId: parameters.get("employeeId"),
    after: parameters.get("after"),
  };
}
export function payrollAmendmentSelectionFromRoute(
  parameters: URLSearchParams,
): PayrollAmendmentSelection {
  const selected = (key: string): number | null => {
    const values = parameters.getAll(key),
      raw = values[0] ?? "";
    if (values.length === 0) return null;
    return values.length === 1 && /^(?:0|[1-9]\d{0,2})$/u.test(raw) ? Number(raw) : Number.NaN;
  };
  return { revision: selected("revision"), after: selected("historyAfter") };
}
export function payrollAmendmentParameters(
  company: CompanyId,
  search: PayrollAmendmentSearch,
): URLSearchParams {
  const parameters = new URLSearchParams({ company, year: String(search.year) });
  if (search.status !== null) parameters.set("status", search.status);
  if (search.query) parameters.set("query", search.query);
  if (search.employeeId !== null) parameters.set("employeeId", search.employeeId);
  if (search.after !== null) parameters.set("after", search.after);
  return parameters;
}
export function payrollAmendmentPath(
  company: CompanyId,
  id: string,
  parameters: URLSearchParams,
): string {
  const next = new URLSearchParams(parameters);
  next.set("company", company);
  return `/payroll/amendments/${encodeURIComponent(id)}?${next}`;
}
