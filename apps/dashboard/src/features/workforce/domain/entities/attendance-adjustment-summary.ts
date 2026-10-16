export type AttendanceAdjustmentSummary = Readonly<{
  id: string;
  revision: number;
  workDate: string;
  correctionId: string;
  actorId: string;
  recordedAt: string;
  reason: string;
}>;
