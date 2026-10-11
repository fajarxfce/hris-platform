import type { WorkHoliday } from "../../domain/entities/work-holiday";

export type HolidayFormValues = { workDate: string; name: string; active: string; reason: string };
export function holidayFormValues(holiday: WorkHoliday | null, today: string): HolidayFormValues {
  return {
    workDate: holiday?.workDate ?? today,
    name: holiday?.name ?? "",
    active: String(holiday?.active ?? true),
    reason: "",
  };
}
