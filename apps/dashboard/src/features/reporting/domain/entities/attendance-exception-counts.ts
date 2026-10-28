export type AttendanceExceptionCounts = Readonly<{
  observedDays: number;
  affectedDays: number;
  affectedEmployments: number;
  pendingDays: number;
  incompleteDays: number;
  pendingEvents: number;
}>;
