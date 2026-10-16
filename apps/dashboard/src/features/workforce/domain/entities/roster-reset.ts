export type RosterReset = Readonly<{
  employeeId: string;
  workDate: string;
  expectedVersion: number;
  reason: string;
}>;
