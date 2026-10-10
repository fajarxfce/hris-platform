export type LeaveBalanceAdjustment = Readonly<{
  employeeId: string;
  typeId: string;
  year: number;
  days: string;
  reason: string;
  expectedVersion: number;
}>;
