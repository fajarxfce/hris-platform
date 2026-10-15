import type { CompanyId } from "../../../../core/domain/identifiers";
/** Retain the originating employee directory filters without importing another feature's UI. */
export function onboardingScheduleRoute(
  company: CompanyId,
  employeeId: string,
  parameters: URLSearchParams,
  today: string,
  tail = "",
) {
  const foreignCompany = parameters.has("company") && parameters.get("company") !== company;
  const asOf = foreignCompany ? today : (parameters.get("asOf") ?? today);
  const query = new URLSearchParams({ company, asOf });
  if (!foreignCompany) {
    const filter = parameters.get("query"),
      after = parameters.get("after");
    if (filter) query.set("query", filter);
    if (after !== null) query.set("after", after);
  }
  const employeePath = `/people/employees/${encodeURIComponent(employeeId)}`,
    path = `${employeePath}/onboarding-schedules`;
  return {
    foreignCompany,
    canonical: parameters.has("company") && parameters.has("asOf"),
    directory: `/people/employees?${query}`,
    employeeTo: `${employeePath}?${query}`,
    listTo: `${path}?${query}`,
    newTo: `${path}/new?${query}`,
    current: `${path}${tail}?${query}`,
    query: query.toString(),
    path,
    asOf,
  };
}
