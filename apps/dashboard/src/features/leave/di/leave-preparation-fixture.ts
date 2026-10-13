import { leaveRecord } from "../../../../tests/fixtures/leave";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import { toLeaveRequestPreview } from "../data/mappers/leave-preview-mapper";
import type { LeaveEvidenceOptionsDto } from "../data/models/leave-evidence-option-dto";
import type { LeavePreparationDirectoryDto } from "../data/models/leave-preparation-employee-dto";
import type { LeaveRequestPreviewDto } from "../data/models/leave-request-preview-dto";
import type { LeaveTypePageDto } from "../data/models/leave-type-dto";
import type { ReviewedLeaveSelection } from "../domain/entities/leave-request-preview";
import type { LeaveRequestSelection } from "../domain/entities/leave-request-selection";

export const preparationCompany = "10000000-0000-4000-8000-000000000001" as CompanyId;
export const preparationAccess = { companyId: preparationCompany, permissions: ["leave.manage"] };
export const preparationOperation = "90000000-0000-4000-8000-000000000010" as OperationId;
export const preparationId = "b1000000-0000-4000-8000-000000000010";
export const preparationSelection: LeaveRequestSelection = {
  employeeId: leaveRecord().employeeId,
  typeId: leaveRecord().policy.typeId,
  days: [
    { workDate: "2026-10-05", portion: "FIRST_HALF" },
    { workDate: "2026-10-06", portion: "FULL" },
  ],
};
export function preparationDirectoryDto(): LeavePreparationDirectoryDto {
  return {
    companyDate: "2026-10-01",
    timezone: "Asia/Jakarta",
    employees: {
      items: [
        {
          id: preparationSelection.employeeId,
          employeeNumber: "EMP-001",
          legalName: "Leave Employee",
          status: "ACTIVE",
          version: 2,
        },
      ],
      nextCursor: null,
    },
  };
}
export function preparationTypesDto(): LeaveTypePageDto {
  return {
    items: [
      {
        id: preparationSelection.typeId,
        code: "ANNUAL",
        name: "Annual leave",
        effectiveFrom: "2025-01-01",
        active: true,
        paid: true,
        allowPartialDays: true,
        minServiceMonths: 0,
        allowedContracts: ["PERMANENT", "FIXED_TERM"],
        maxRequestDays: 30,
        attachmentRequired: false,
        accrual: null,
        version: 3,
        appliedRevision: 2,
      },
    ],
    nextCursor: null,
  };
}
export function preparationEvidenceDto(): LeaveEvidenceOptionsDto {
  return {
    items: [
      {
        documentId: "d1000000-0000-4000-8000-000000000001",
        revisionId: "d2000000-0000-4000-8000-000000000001",
        revisionNumber: 2,
        title: "Supporting letter",
        fileName: "letter.pdf",
        mediaType: "application/pdf",
        size: 1200,
        validatedAt: "2026-09-30T10:00:00Z",
      },
    ],
    nextCursor: null,
  };
}
export function preparationPreviewDto(): LeaveRequestPreviewDto {
  const record = leaveRecord();
  return {
    employeeId: record.employeeId,
    evaluatedAt: "2026-10-01T10:00:00Z",
    timezone: "Asia/Jakarta",
    policy: record.policy,
    days: record.days,
    chargedDays: "0.5",
    excludedDates: ["2026-10-06"],
    balances: [
      {
        year: 2026,
        availableDays: "12.5",
        reservedDays: "1",
        consumedDays: "1",
        requestedDays: "0.5",
        remainingDays: "12",
        version: 4,
      },
    ],
    revision: "a".repeat(64),
  };
}
export function preparationReview(): ReviewedLeaveSelection {
  return {
    selection: preparationSelection,
    preview: toLeaveRequestPreview(
      preparationPreviewDto(),
      preparationCompany,
      preparationSelection,
    ),
  };
}
