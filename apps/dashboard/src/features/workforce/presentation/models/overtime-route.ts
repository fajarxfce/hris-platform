import type { CompanyId } from "../../../../core/domain/identifiers";
import type { OvertimeSearchInput } from "../../domain/entities/overtime-search";

export const overtimePath = "/workforce/overtime";
export const overtimeRequestPath = (id: string) => `${overtimePath}/${encodeURIComponent(id)}`;

export function overtimeSearchFromRoute(
  parameters: URLSearchParams,
  company: CompanyId,
  today: string,
): OvertimeSearchInput {
  const first = `${today.slice(0, 7)}-01`;
  const until = new Date(Date.UTC(Number(today.slice(0, 4)), Number(today.slice(5, 7)), 0))
    .toISOString()
    .slice(0, 10);
  if (parameters.has("company") && parameters.get("company") !== company)
    return { from: first, until, employeeId: null, status: null, after: null };
  return {
    from: parameters.get("from") ?? first,
    until: parameters.get("until") ?? until,
    employeeId: parameters.get("employeeId"),
    status: parameters.get("status"),
    after: parameters.get("after"),
  };
}

export function overtimeParameters(
  company: CompanyId,
  search: OvertimeSearchInput,
): URLSearchParams {
  const query = new URLSearchParams({ company, from: search.from, until: search.until });
  if (search.employeeId !== null) query.set("employeeId", search.employeeId);
  if (search.status !== null) query.set("status", search.status);
  if (search.after !== null) query.set("after", search.after);
  return query;
}
