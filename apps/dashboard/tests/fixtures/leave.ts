import type { LeaveRequestDetailsDto } from "../../src/features/leave/data/models/leave-request-details-dto";
import type { LeaveRequestSummaryDto } from "../../src/features/leave/data/models/leave-request-summary-dto";

export const leaveId = (company = 0, index = 1) =>
  `b1000000-0000-4000-8000-${String(company * 100 + index).padStart(12, "0")}`;
export const leaveEmployeeId = (company = 0, index = 1) =>
  `b2000000-0000-4000-8000-${String(company * 100 + index).padStart(12, "0")}`;
export function leaveRecord(company = 0, index = 1): LeaveRequestDetailsDto {
  const authorId = "20000000-0000-4000-8000-000000000099";
  const submittedAt = new Date(Date.UTC(2026, 8, 30, 12) - index * 60_000).toISOString();
  return {
    id: leaveId(company, index),
    employeeId: leaveEmployeeId(company, index),
    employeeNumber: `EMP-${company}-${index}`,
    employeeName: `${company === 0 ? "North" : "South"} Employee ${String(index).padStart(2, "0")}`,
    ownerAccountId: authorId,
    authorId,
    submittedAt,
    policy: {
      typeId: "b3000000-0000-4000-8000-000000000001",
      code: "ANNUAL",
      revision: 2,
      name: "Annual leave",
      paid: true,
      allowPartialDays: true,
      minServiceMonths: 12,
      allowedContracts: ["PERMANENT", "FIXED_TERM"],
      maxRequestDays: 30,
      attachmentRequired: false,
    },
    days: [
      {
        workDate: "2026-10-05",
        portion: "FIRST_HALF",
        chargedDays: "0.5",
        startsAt: "2026-10-05T15:00:00Z",
        endsAt: "2026-10-05T23:00:00Z",
        plannedMinutes: 450,
        chargedMinutes: 225,
        shiftId: "b4000000-0000-4000-8000-000000000001",
        shiftRevision: 3,
        scheduleOrigin: "PATTERN",
        scheduleRevision: 1,
      },
    ],
    chargedDays: "0.5",
    reason: "Personal leave",
    status: "PENDING",
    version: 0,
    approval: {
      id: `b5000000-0000-4000-8000-${String(company * 100 + index).padStart(12, "0")}`,
      authorId,
      requesterId: authorId,
      status: "PENDING",
      currentStep: 0,
      stages: [["20000000-0000-4000-8000-000000000001"]],
      version: 0,
    },
    cancellation: null,
    history: {
      items: [
        {
          version: 0,
          kind: "SUBMITTED",
          status: "PENDING",
          cancellationApprovalId: null,
          actorId: authorId,
          recordedAt: submittedAt,
          reason: "Personal leave",
        },
      ],
      nextCursor: null,
    },
    availableActions: ["DECIDE"],
    attachments: [],
  };
}
export function leaveSummary(record: LeaveRequestDetailsDto): LeaveRequestSummaryDto {
  return {
    id: record.id,
    employeeId: record.employeeId,
    employeeNumber: record.employeeNumber,
    employeeName: record.employeeName,
    typeCode: record.policy.code,
    typeName: record.policy.name,
    from: "2026-10-05",
    until: "2026-10-05",
    chargedDays: record.chargedDays,
    status: record.status,
    submittedAt: record.submittedAt,
    version: record.version,
  };
}

/** One approval followed by independently requested and withdrawn cancellation attempts. */
export function leaveWithHistory(): LeaveRequestDetailsDto {
  const record = leaveRecord();
  const cancellation = {
    ...record.approval,
    id: "b6000000-0000-4000-8000-000000000001",
    status: "CANCELLED" as const,
    version: 1,
  };
  return {
    ...record,
    status: "APPROVED",
    version: 23,
    approval: { ...record.approval, status: "APPROVED", version: 1 },
    cancellation,
    availableActions: ["REQUEST_CANCELLATION"],
    history: {
      items: Array.from({ length: 24 }, (_, index) => {
        const version = 23 - index;
        return {
          version,
          kind:
            version === 0
              ? "SUBMITTED"
              : version === 1
                ? "DECIDED"
                : version % 2 === 0
                  ? "CANCELLATION_REQUESTED"
                  : "WITHDRAWN",
          status:
            version === 0
              ? "PENDING"
              : version === 1 || version % 2 === 1
                ? "APPROVED"
                : "CANCELLATION_PENDING",
          cancellationApprovalId: version < 2 ? null : cancellation.id,
          actorId: record.authorId,
          recordedAt: new Date(
            new Date(record.submittedAt).getTime() + version * 60_000,
          ).toISOString(),
          reason: version === 0 ? record.reason : "Leave plan changed",
        };
      }),
      nextCursor: null,
    },
  };
}
