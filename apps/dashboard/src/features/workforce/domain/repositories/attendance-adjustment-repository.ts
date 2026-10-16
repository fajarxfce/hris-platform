import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { AttendanceAdjustmentChange } from "../entities/attendance-adjustment-change";
import type { AttendanceAdjustmentDetails } from "../entities/attendance-adjustment-details";
import type { AttendanceAdjustmentPage } from "../entities/attendance-adjustment-page";
import type { AttendanceAdjustmentReview } from "../entities/attendance-adjustment-review";

export interface AttendanceAdjustmentRepository {
  review(
    company: CompanyId,
    employee: EmployeeId,
    date: string,
    signal: AbortSignal,
  ): Promise<Result<AttendanceAdjustmentReview>>;
  history(
    company: CompanyId,
    employee: EmployeeId,
    month: string,
    before: string | null,
    signal: AbortSignal,
  ): Promise<Result<AttendanceAdjustmentPage>>;
  details(
    company: CompanyId,
    employee: EmployeeId,
    month: string,
    id: string,
    signal: AbortSignal,
  ): Promise<Result<AttendanceAdjustmentDetails>>;
  adjust(
    company: CompanyId,
    operation: OperationId,
    change: AttendanceAdjustmentChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
