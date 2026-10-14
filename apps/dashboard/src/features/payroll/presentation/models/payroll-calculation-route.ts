export function payrollCalculationsPath(company: string, period: string) {
  return `/payroll/periods/${encodeURIComponent(period)}/calculations?${new URLSearchParams({ company })}`;
}
export function payrollRunPath(
  company: string,
  id: string,
  after: string | null = null,
  tab = "results",
) {
  const parameters = new URLSearchParams({ company, tab });
  if (after !== null) parameters.set("after", after);
  return `/payroll/runs/${encodeURIComponent(id)}?${parameters}`;
}
export function payrollRunEmployeePath(
  company: string,
  id: string,
  employee: string,
  after: string | null = null,
) {
  const parameters = new URLSearchParams({ company });
  if (after !== null) parameters.set("after", after);
  return `/payroll/runs/${encodeURIComponent(id)}/employees/${encodeURIComponent(employee)}?${parameters}`;
}
