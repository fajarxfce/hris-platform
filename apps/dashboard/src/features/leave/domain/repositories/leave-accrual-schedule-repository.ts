import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type {
  LeaveAccrualScheduleId,
  LeaveAccrualSchedulePage,
} from "../entities/leave-accrual-schedule";
import type { LeaveAccrualScheduleCreation } from "../entities/leave-accrual-schedule-command";
import type { LeaveAccrualScheduleDetails } from "../entities/leave-accrual-schedule-details";
import type {
  LeaveAccrualScheduleReview,
  LeaveAccrualScheduleSelection,
} from "../entities/leave-accrual-schedule-review";
export interface LeaveAccrualScheduleRepository {
  review(
    company: CompanyId,
    selection: LeaveAccrualScheduleSelection,
    signal: AbortSignal,
  ): Promise<Result<LeaveAccrualScheduleReview>>;
  list(
    company: CompanyId,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<LeaveAccrualSchedulePage>>;
  get(
    company: CompanyId,
    id: LeaveAccrualScheduleId,
    signal: AbortSignal,
  ): Promise<Result<LeaveAccrualScheduleDetails>>;
  create(
    operation: OperationId,
    input: LeaveAccrualScheduleCreation,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
