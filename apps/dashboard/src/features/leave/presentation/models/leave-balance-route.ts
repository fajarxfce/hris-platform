import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveBalanceQuery } from "../../domain/entities/leave-balance-query";

export function balanceQuery(parameters: URLSearchParams, defaultYear: string): LeaveBalanceQuery {
  return { year: parameters.get("year") ?? defaultYear, after: parameters.get("after") };
}
export function balanceParameters(
  company: CompanyId,
  query: LeaveBalanceQuery,
  directoryAfter: string | null = null,
): URLSearchParams {
  const parameters = new URLSearchParams({ company, year: query.year });
  if (query.after !== null) parameters.set("after", query.after);
  if (directoryAfter !== null) parameters.set("directoryAfter", directoryAfter);
  return parameters;
}
