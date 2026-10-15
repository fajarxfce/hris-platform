import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { LifecycleCaseId } from "../entities/lifecycle-case";
import type { OffboardingSchedule, OffboardingScheduleId } from "../entities/offboarding-schedule";
import type {
  OffboardingScheduleChange,
  OffboardingScheduleCreation,
} from "../entities/offboarding-schedule-command";
import type { OffboardingScheduleReview } from "../entities/offboarding-schedule-review";
export interface OffboardingScheduleRepository {
  review(
    company: CompanyId,
    caseId: LifecycleCaseId,
    signal: AbortSignal,
  ): Promise<Result<OffboardingScheduleReview>>;
  list(
    company: CompanyId,
    caseId: LifecycleCaseId,
    signal: AbortSignal,
  ): Promise<Result<readonly OffboardingSchedule[]>>;
  get(
    company: CompanyId,
    caseId: LifecycleCaseId,
    id: OffboardingScheduleId,
    signal: AbortSignal,
  ): Promise<Result<OffboardingSchedule>>;
  schedule(
    company: CompanyId,
    operation: OperationId,
    input: OffboardingScheduleCreation,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  cancel(
    company: CompanyId,
    operation: OperationId,
    input: OffboardingScheduleChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  recover(
    company: CompanyId,
    operation: OperationId,
    input: OffboardingScheduleChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
