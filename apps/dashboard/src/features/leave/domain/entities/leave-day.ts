export const leavePortions = ["FULL", "FIRST_HALF", "SECOND_HALF"] as const;
export type LeaveDay = Readonly<{
  workDate: string;
  portion: (typeof leavePortions)[number];
  chargedDays: string;
  startsAt: string;
  endsAt: string;
  plannedMinutes: number;
  chargedMinutes: number;
  shiftId: string;
  shiftRevision: number;
  scheduleOrigin: string;
  scheduleRevision: number;
}>;
