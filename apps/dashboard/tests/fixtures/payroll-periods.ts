import type {
  PayrollPeriodCreationDto,
  PayrollPeriodDetailsDto,
  PayrollPeriodDto,
} from "../../src/features/payroll/data/models/payroll-period-dto";
export const periodCompany = "11111111-1111-4111-8111-111111111111",
  periodId = "ea800000-0000-4000-8000-000000000001",
  periodActor = "ea200000-0000-4000-8000-000000000001";
export const periodEmployee = (index = 1) => ({
  id: `ea100000-0000-4000-8000-${String(index).padStart(12, "0")}`,
  employeeNumber: `PAY-${String(index).padStart(3, "0")}`,
  name: `Payroll Employee ${String(index).padStart(3, "0")}`,
  employmentVersion: 0,
});
export function periodRecord(changes: Partial<PayrollPeriodDto> = {}): PayrollPeriodDto {
  return {
    id: periodId,
    earningsMonth: "2026-09",
    plannedPaymentMonth: "2026-10",
    plannedPaymentDate: "2026-10-02",
    timezone: "Asia/Jakarta",
    participantCount: 2,
    authorId: periodActor,
    createdAt: "2026-10-01T00:00:00Z",
    status: "DRAFT",
    version: 0,
    currentRunId: null,
    ...changes,
  };
}
export function periodDetails(changes: Partial<PayrollPeriodDto> = {}): PayrollPeriodDetailsDto {
  const period = periodRecord(changes);
  return {
    period,
    members: {
      items: Array.from({ length: Math.min(period.participantCount, 50) }, (_, i) => {
        const employee = periodEmployee(i + 1);
        return {
          employeeId: employee.id,
          employeeNumber: employee.employeeNumber,
          name: employee.name,
          inputVersion: null,
          inputStatus: null,
        };
      }),
      nextCursor: period.participantCount > 50 ? periodEmployee(50).id : null,
    },
    history: {
      items: Array.from({ length: Math.min(period.version + 1, 20) }, (_, version) => ({
        version,
        status: version === period.version ? period.status : "DRAFT",
        actorId: periodActor,
        recordedAt: "2026-10-01T00:00:00Z",
        reason: version === 0 ? "Prepare September payroll" : "Replace September payroll",
        runId: version === period.version ? period.currentRunId : null,
      })),
      nextCursor: period.version >= 20 ? "19" : null,
    },
  };
}
export const periodCreation = (): PayrollPeriodCreationDto => ({
  id: periodId,
  earningsMonth: "2026-09",
  plannedPaymentDate: "2026-10-02",
  employeeIds: [periodEmployee().id, periodEmployee(2).id],
  reason: "Prepare September payroll",
});
