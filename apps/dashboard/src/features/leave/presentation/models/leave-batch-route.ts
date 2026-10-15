import { type CompanyId, isUuid } from "../../../../core/domain/identifiers";
import type { LeaveBatchKind } from "../../domain/entities/leave-batch";
/** Company replacement discards resource cursors; result paging retains the originating directory. */
export function leaveBatchRoute(company: CompanyId, parameters: URLSearchParams, tail = "") {
  const foreignCompany = parameters.has("company") && parameters.get("company") !== company;
  const after = foreignCompany ? null : parameters.get("after"),
    resultAfter = foreignCompany ? null : parameters.get("resultAfter");
  const selectedKind = foreignCompany ? null : parameters.get("kind");
  const kind: LeaveBatchKind = selectedKind === "YEAR_CLOSE" ? "YEAR_CLOSE" : "ACCRUAL";
  const query = new URLSearchParams({ company });
  if (after !== null) query.set("after", after);
  const requestedSchedule = parameters.get("schedule"),
    requestedScheduleAfter = parameters.get("scheduleAfter");
  const schedule =
    !foreignCompany && tail !== "/new" && requestedSchedule && isUuid(requestedSchedule)
      ? requestedSchedule.toLowerCase()
      : null;
  const scheduleAfter =
    schedule && requestedScheduleAfter && isUuid(requestedScheduleAfter)
      ? requestedScheduleAfter.toLowerCase()
      : null;
  const resultQuery = new URLSearchParams(query),
    scheduleQuery = new URLSearchParams({ company });
  if (schedule) {
    resultQuery.delete("after");
    resultQuery.set("schedule", schedule);
    if (scheduleAfter) {
      resultQuery.set("scheduleAfter", scheduleAfter);
      scheduleQuery.set("after", scheduleAfter);
    }
  }
  const detailQuery = new URLSearchParams(resultQuery);
  if (resultAfter !== null) detailQuery.set("resultAfter", resultAfter);
  const currentQuery = new URLSearchParams(tail === "/new" ? query : detailQuery);
  if (tail === "/new") currentQuery.set("kind", kind);
  const path = "/leave/batches";
  return {
    foreignCompany,
    canonical: parameters.has("company") && (tail !== "/new" || selectedKind !== null),
    invalidKind:
      tail === "/new" && selectedKind !== null && !["ACCRUAL", "YEAR_CLOSE"].includes(selectedKind),
    path,
    query: query.toString(),
    detailQuery: detailQuery.toString(),
    resultQuery: resultQuery.toString(),
    schedule,
    backTo: schedule ? `/leave/accrual-schedules/${schedule}?${scheduleQuery}` : `${path}?${query}`,
    kind,
    after,
    resultAfter,
    listTo: `${path}?${query}`,
    current: `${path}${tail}?${currentQuery}`,
    newAccrualTo: `${path}/new?${query}&kind=ACCRUAL`,
    newYearCloseTo: `${path}/new?${query}&kind=YEAR_CLOSE`,
  };
}
export function initialLeaveBatchPeriod(today: string, kind: LeaveBatchKind): string {
  const year = Number(today.slice(0, 4)),
    month = Number(today.slice(5, 7));
  return kind === "YEAR_CLOSE" || month === 1
    ? `${year - 1}-12`
    : `${year}-${String(month - 1).padStart(2, "0")}`;
}
