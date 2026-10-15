import type { Failure } from "../../../../core/domain/result";
import type { LeaveCalendar } from "../../domain/entities/leave-calendar";
import type { LeaveCalendarEntries } from "../../domain/entities/leave-calendar-entry";

export type LeaveCalendarState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  calendar: LeaveCalendar | null;
  date: string | null;
  entriesStage: "idle" | "loading" | "ready" | "unavailable";
  entries: LeaveCalendarEntries | null;
  after: string | null;
  failure: Failure | null;
  entriesFailure: Failure | null;
}>;
export const initialLeaveCalendarState: LeaveCalendarState = Object.freeze({
  stage: "loading",
  calendar: null,
  date: null,
  entriesStage: "idle",
  entries: null,
  after: null,
  failure: null,
  entriesFailure: null,
});
