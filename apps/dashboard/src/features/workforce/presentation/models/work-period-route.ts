import type { CompanyId } from "../../../../core/domain/identifiers";
import type { WorkPeriodIntent } from "../../domain/entities/work-period-action-context";

export const workPeriodsPath = "/workforce/periods";
export const workPeriodPath = (company: CompanyId, month: string) =>
  `${workPeriodsPath}/${encodeURIComponent(month)}?${new URLSearchParams({ company })}`;
export const workPeriodActionPath = (company: CompanyId, month: string, intent: WorkPeriodIntent) =>
  `${workPeriodsPath}/${encodeURIComponent(month)}/${intent}?${new URLSearchParams({ company })}`;
export const workPeriodEmployeePath = (company: CompanyId, month: string, employee: string) =>
  `${workPeriodsPath}/${encodeURIComponent(month)}/employees/${encodeURIComponent(employee)}?${new URLSearchParams({ company })}`;

/** Navigation default only; command eligibility uses the reviewed server date. */
export function defaultWorkPeriodMonth(companyDate: string): string {
  const previous = new Date(`${companyDate.slice(0, 7)}-01T00:00:00Z`);
  previous.setUTCMonth(previous.getUTCMonth() - 1);
  return previous.toISOString().slice(0, 7);
}
