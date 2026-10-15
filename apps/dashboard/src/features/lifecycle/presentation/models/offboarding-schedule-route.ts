import type { CompanyId } from "../../../../core/domain/identifiers";
import { lifecycleCaseParameters, lifecycleCaseSearch } from "./lifecycle-case-route";
/** Preserve the case directory scope, without carrying task/history state into a schedule. */
export function offboardingScheduleRoute(
  company: CompanyId,
  caseId: string,
  parameters: URLSearchParams,
  tail = "",
) {
  const query = lifecycleCaseParameters(company, lifecycleCaseSearch(parameters, company));
  const casePath = `/people/lifecycle/cases/${encodeURIComponent(caseId)}`;
  const schedules = `${casePath}/offboarding-schedules`;
  return {
    foreignCompany: parameters.has("company") && parameters.get("company") !== company,
    canonical: parameters.has("company") && parameters.has("status"),
    directory: `/people/lifecycle/cases?${query}`,
    caseTo: `${casePath}?${query}`,
    listTo: `${schedules}?${query}`,
    newTo: `${schedules}/new?${query}`,
    current: `${schedules}${tail}?${query}`,
    query: query.toString(),
    path: schedules,
  };
}
