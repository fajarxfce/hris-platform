export type AttendanceCorrection = Readonly<{
  id: string;
  clockIn: string | null;
  clockOut: string | null;
  breakMinutes: number;
  actorId: string;
  recordedAt: string;
  reason: string;
  version: number;
}>;
