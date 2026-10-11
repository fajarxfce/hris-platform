import type { ShiftSnapshot } from "./shift-snapshot";

export const weekdays = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
] as const;
export type Weekday = (typeof weekdays)[number];
export type ScheduleAssignment = Readonly<{
  effectiveFrom: string;
  revision: number;
  days: Readonly<Partial<Record<Weekday, ShiftSnapshot>>>;
}>;
