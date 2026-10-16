import type { CompanyId } from "../../../../core/domain/identifiers";
import type { AttendanceDay } from "./attendance-day";
import type { ClosedWorkDay } from "./closed-work-day";
import type { WorkPeriod } from "./work-period";
import type { WorkforceEmployee } from "./workforce-employee";

export type AttendanceAdjustmentReview = Readonly<{
  companyId: CompanyId;
  employee: WorkforceEmployee;
  period: WorkPeriod;
  original: ClosedWorkDay;
  current: ClosedWorkDay;
  attendance: AttendanceDay;
  adjustmentRevision: number;
  revision: string;
  canCorrect: boolean;
  canReviewEvidence: boolean;
}>;
