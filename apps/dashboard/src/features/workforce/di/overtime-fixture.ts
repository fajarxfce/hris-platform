import type { OvertimeDetailsDto } from "../data/models/overtime-details-dto";
import type { OvertimeRequestDto } from "../data/models/overtime-request-dto";
import type { OvertimeStatus } from "../domain/entities/overtime-request";
import { company, employeeId, identifier } from "./calendar-fixture";

export { company, employeeId, identifier };
export const overtimeId = identifier(701);
export const overtimeDate = "2026-10-12";
export const overtimeAccess = {
  companyId: company,
  permissions: ["overtime.read", "overtime.manage"],
};
export const overtimeSearch = {
  employeeId: null,
  from: overtimeDate,
  until: overtimeDate,
  status: null,
  after: null,
};

export function overtimeDtoFixture(
  status: OvertimeStatus = "PENDING",
  version = status === "PLANNED" ? 0 : status === "PENDING" ? 1 : 2,
): OvertimeRequestDto {
  const actual =
    status === "PLANNED"
      ? null
      : {
          startsAt: "2026-10-12T07:15:00Z",
          endsAt: "2026-10-12T09:45:00Z",
          breakMinutes: 15,
          workedMinutes: 135,
        };
  return {
    id: overtimeId,
    employeeId,
    employeeNumber: "EMP-001",
    employeeName: "Avery Tan",
    requesterAccountId: identifier(21),
    authorId: identifier(21),
    createdAt: "2026-10-11T03:00:00Z",
    workDate: overtimeDate,
    timezone: "Asia/Jakarta",
    schedule: {
      workDate: overtimeDate,
      kind: "OFF",
      shift: null,
      startsAt: null,
      endsAt: null,
      plannedMinutes: 0,
      origin: "HOLIDAY",
      originVersion: 0,
      holidayId: identifier(601),
    },
    requested: {
      startsAt: "2026-10-12T07:00:00Z",
      endsAt: "2026-10-12T10:00:00Z",
      breakMinutes: 0,
      workedMinutes: 180,
    },
    reason: "Inventory count",
    status,
    actual,
    submittedBy: actual ? identifier(21) : null,
    submittedAt: actual ? "2026-10-12T10:00:00Z" : null,
    approvalId: actual ? identifier(702) : null,
    approvedMinutes: status === "APPROVED" ? 135 : 0,
    decidedAt: ["APPROVED", "REJECTED", "WITHDRAWN"].includes(status)
      ? "2026-10-12T11:00:00Z"
      : null,
    version,
  };
}

export function overtimeDetailsDtoFixture(
  status: OvertimeStatus = "PENDING",
  version?: number,
  after: string | null = null,
): OvertimeDetailsDto {
  const request = overtimeDtoFixture(status, version);
  const first = Math.min(request.version, after === null ? request.version : Number(after) - 1);
  const items = Array.from({ length: Math.max(0, Math.min(50, first + 1)) }, (_, index) => {
    const revision = first - index;
    return {
      revision,
      kind:
        revision === 0
          ? ("PLANNED" as const)
          : revision === 1
            ? ("ACTUAL_SUBMITTED" as const)
            : status === "WITHDRAWN" && revision === request.version
              ? ("WITHDRAWN" as const)
              : ("DECIDED" as const),
      status:
        revision === 0
          ? ("PLANNED" as const)
          : revision === request.version
            ? request.status
            : ("PENDING" as const),
      actual: revision === 0 || request.actual === null ? null : { ...request.actual },
      approvedMinutes: revision === request.version ? request.approvedMinutes : 0,
      approvalId: revision === 0 ? null : request.approvalId,
      actorId: identifier(revision < 2 ? 21 : 22),
      recordedAt: revision === 0 ? request.createdAt : "2026-10-12T11:00:00Z",
      reason: revision === 0 ? request.reason : "Work verified",
    };
  });
  return {
    request,
    approval:
      request.approvalId === null
        ? null
        : {
            id: request.approvalId,
            status:
              status === "WITHDRAWN" ? "CANCELLED" : status === "PLANNED" ? "PENDING" : status,
            stages: [[identifier(22)], [identifier(23)]],
            currentStep: 0,
            version: Math.max(0, request.version - 1),
          },
    history: { items, nextCursor: first >= 50 ? String(first - 49) : null },
    actions:
      status === "PLANNED"
        ? ["SUBMIT_ACTUAL", "WITHDRAW"]
        : status === "PENDING"
          ? ["APPROVE", "REJECT"]
          : [],
  };
}
