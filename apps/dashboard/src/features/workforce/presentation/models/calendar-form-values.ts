import type { CalendarReview } from "../../domain/entities/calendar-review";
import { type Weekday, weekdays } from "../../domain/entities/schedule-assignment";
import type { ShiftReference } from "../../domain/entities/shift-reference";
import type { ShiftSnapshot } from "../../domain/entities/shift-snapshot";

export type WeeklyScheduleFormValues = {
  days: Record<Weekday, ShiftSnapshot | null>;
  reason: string;
};
export type RosterDayFormValues = { shift: ShiftSnapshot | null; reason: string; reset: boolean };
export function weeklyScheduleFormValues(review: CalendarReview | null): WeeklyScheduleFormValues {
  return {
    days: Object.fromEntries(
      weekdays.map((day) => [day, review?.assignmentAtStart?.days[day] ?? null]),
    ) as Record<Weekday, ShiftSnapshot | null>,
    reason: "",
  };
}
export function rosterDayFormValues(review: CalendarReview | null): RosterDayFormValues {
  const day = review?.days[0];
  return { shift: day?.kind === "WORK" ? day.shift : null, reason: "", reset: false };
}
export function weeklyScheduleReferences(
  days: WeeklyScheduleFormValues["days"],
): Readonly<Partial<Record<Weekday, ShiftReference>>> {
  return Object.freeze(
    Object.fromEntries(
      weekdays.flatMap((day) => {
        const shift = days[day];
        return shift ? [[day, Object.freeze({ id: shift.id, version: shift.revision })]] : [];
      }),
    ),
  );
}
