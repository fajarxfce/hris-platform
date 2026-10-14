import type { CompanyId } from "../../../../core/domain/identifiers";

export function payrollPolicyQuery(
  parameters: URLSearchParams,
  company: CompanyId,
  currentMonth: string,
) {
  const replaced = parameters.has("company") && parameters.get("company") !== company;
  return {
    month: replaced ? currentMonth : (parameters.get("month") ?? currentMonth),
    after: replaced ? null : parameters.get("historyAfter"),
  };
}
export function payrollPolicyParameters(
  company: CompanyId,
  month: string,
  after: string | null = null,
) {
  const parameters = new URLSearchParams({ company, month });
  if (after !== null) parameters.set("historyAfter", after);
  return parameters;
}
