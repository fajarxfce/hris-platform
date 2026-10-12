import type { CalendarRange } from "./calendar-range";

export type AttendanceExceptionSearch = Readonly<
  CalendarRange & {
    kind: "PENDING" | "INCOMPLETE" | null;
    after: string | null;
  }
>;

/** Browser input is validated before it becomes a repository query. */
export type AttendanceExceptionSearchInput = Readonly<
  Omit<AttendanceExceptionSearch, "kind"> & { kind: string | null }
>;
