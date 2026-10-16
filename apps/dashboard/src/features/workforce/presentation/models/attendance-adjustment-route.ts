import type { CompanyId } from "../../../../core/domain/identifiers";
import { workPeriodEmployeePath } from "./work-period-route";

export function attendanceAdjustmentHistoryPath(
  company: CompanyId,
  month: string,
  employee: string,
  before: string | null = null,
) {
  const base = workPeriodEmployeePath(company, month, employee).split("?")[0];
  const query = new URLSearchParams({ company });
  if (before !== null) query.set("before", before);
  return `${base}/adjustments?${query}`;
}
export function attendanceAdjustmentPath(
  company: CompanyId,
  month: string,
  employee: string,
  id: string,
  before: string | null = null,
) {
  const history = attendanceAdjustmentHistoryPath(company, month, employee, before);
  const [base, query] = history.split("?");
  return `${base}/${encodeURIComponent(id)}?${query}`;
}
export function attendanceAdjustmentReviewPath(
  company: CompanyId,
  month: string,
  employee: string,
  date: string,
) {
  const base = workPeriodEmployeePath(company, month, employee).split("?")[0];
  return `${base}/days/${encodeURIComponent(date)}/adjustment?${new URLSearchParams({ company })}`;
}
