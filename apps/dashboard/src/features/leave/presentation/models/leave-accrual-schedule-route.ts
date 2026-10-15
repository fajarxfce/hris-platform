import type { CompanyId } from "../../../../core/domain/identifiers";
export function leaveAccrualScheduleRoute(
  company: CompanyId,
  parameters: URLSearchParams,
  tail = "",
) {
  const foreignCompany = parameters.has("company") && parameters.get("company") !== company;
  const after = foreignCompany ? null : parameters.get("after");
  const query = new URLSearchParams({ company });
  if (after !== null) query.set("after", after);
  const path = "/leave/accrual-schedules";
  return {
    foreignCompany,
    canonical: parameters.has("company"),
    path,
    after,
    query: query.toString(),
    listTo: `${path}?${query}`,
    current: `${path}${tail}?${query}`,
    createTo: `${path}/new?${query}`,
    batchesTo: `/leave/batches?company=${encodeURIComponent(company)}`,
  };
}

/** Retains a schedule's history cursor separately from employee outcome paging. */
export function leaveAccrualSchedulePeriodLink(
  company: CompanyId,
  schedule: string,
  batch: string,
  after: string | null,
): string {
  const query = new URLSearchParams({ company, schedule });
  if (after !== null) query.set("scheduleAfter", after);
  return `/leave/batches/${encodeURIComponent(batch)}?${query}`;
}
