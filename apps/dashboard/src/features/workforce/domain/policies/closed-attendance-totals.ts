import type { ClosedWorkDay, WorkDayFact } from "../entities/closed-work-day";

export function closedAttendanceTotals(days: readonly ClosedWorkDay[]) {
  const facts: Record<WorkDayFact, number> = {
    WORKED: 0,
    ABSENCE_RECORDED: 0,
    UNRECORDED: 0,
    OFF: 0,
    UNASSIGNED: 0,
  };
  let acceptedMinutes = 0,
    overtimeMinutes = 0;
  for (const day of days) {
    facts[day.fact] += 1;
    acceptedMinutes += day.acceptedMinutes;
    for (const request of day.overtime) overtimeMinutes += request.approvedMinutes;
  }
  return Object.freeze({ acceptedMinutes, overtimeMinutes, facts: Object.freeze(facts) });
}
