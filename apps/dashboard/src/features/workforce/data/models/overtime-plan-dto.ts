export type OvertimePlanDto = Readonly<{
  id: string;
  employeeId: string;
  expectedEmploymentVersion: number;
  workDate: string;
  requested: Readonly<{ startsAt: string; endsAt: string; breakMinutes: number }>;
  reason: string;
  planningRevision: string;
}>;
