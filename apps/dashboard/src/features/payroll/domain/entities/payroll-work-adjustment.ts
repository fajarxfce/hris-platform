export type PayrollWorkAdjustment = Readonly<{
  id: string;
  revision: number;
  workDate: string;
  recordedAt: string;
  reason: string;
}>;
