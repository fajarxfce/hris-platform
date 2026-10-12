export type OvertimeActualDto = {
  expectedVersion: number;
  actual: { startsAt: string; endsAt: string; breakMinutes: number };
  reason: string;
};
