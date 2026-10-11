export type AttendanceCorrectionChange = Readonly<{
  employeeId: string;
  workDate: string;
  clockIn: string | null;
  clockOut: string | null;
  breakMinutes: number;
  expectedVersion: number | null;
  reason: string;
}>;
