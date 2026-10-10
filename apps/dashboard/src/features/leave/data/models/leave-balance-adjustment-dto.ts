export type LeaveBalanceAdjustmentDto = Readonly<{
  days: string;
  reason: string;
  expectedVersion: number;
}>;
