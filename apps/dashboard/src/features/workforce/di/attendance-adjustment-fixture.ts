import { toAttendanceAdjustmentReview } from "../data/mappers/attendance-adjustment-mapper";
import type {
  AttendanceAdjustmentDetailsDto,
  AttendanceAdjustmentReviewDto,
} from "../data/models/attendance-adjustment-dto";
import type { AttendanceAdjustmentChange } from "../domain/entities/attendance-adjustment-change";
import {
  attendanceAccess,
  attendanceDtoFixture,
  company,
  employeeId,
  identifier,
} from "./attendance-fixture";
import { periodMonth, workPeriodSnapshotDtoFixture } from "./work-period-fixture";

export { attendanceAccess as adjustmentAccess, company, employeeId, identifier, periodMonth };
export const adjustmentDate = `${periodMonth}-05`;
export const adjustmentId = identifier(980);
export const adjustmentOperation = identifier(981);
export function adjustmentReviewDto(): AttendanceAdjustmentReviewDto {
  const snapshot = workPeriodSnapshotDtoFixture();
  const original = snapshot.days[4];
  const attendance = attendanceDtoFixture({ from: adjustmentDate, until: adjustmentDate })[0];
  if (!original || !attendance) throw new Error("Closed day fixture required");
  return {
    employee: snapshot.employee,
    period: snapshot.period,
    original,
    current: structuredClone(original),
    attendance,
    adjustmentRevision: 0,
    revision: "a".repeat(64),
    canCorrect: true,
    canReviewEvidence: true,
  };
}
export function adjustmentReviewFixture() {
  return toAttendanceAdjustmentReview(adjustmentReviewDto(), company, employeeId, adjustmentDate);
}
export function adjustmentChange(): AttendanceAdjustmentChange {
  return {
    companyId: company,
    employeeId,
    workDate: adjustmentDate,
    reviewRevision: "a".repeat(64),
    expectedAdjustmentRevision: 0,
    clockIn: null,
    clockOut: null,
    breakMinutes: 0,
    reason: "Reviewed absence",
    decisions: [{ eventId: identifier(801), expectedVersion: 0, decision: "REJECT" }],
  };
}
export function adjustmentDetailsDto(revision = 1): AttendanceAdjustmentDetailsDto {
  const snapshot = workPeriodSnapshotDtoFixture();
  const day = snapshot.days[4];
  if (!day) throw new Error("Closed day fixture required");
  const correction = identifier(985 + revision);
  snapshot.adjustmentsSupported = true;
  day.fact = "ABSENCE_RECORDED";
  day.acceptedMinutes = 0;
  day.correctionId = correction;
  return {
    summary: {
      id: revision === 1 ? adjustmentId : identifier(990 + revision),
      revision,
      workDate: adjustmentDate,
      correctionId: correction,
      actorId: identifier(800),
      recordedAt: "2026-10-13T05:30:00Z",
      reason: `Reviewed absence ${revision}`,
    },
    snapshot,
  };
}
