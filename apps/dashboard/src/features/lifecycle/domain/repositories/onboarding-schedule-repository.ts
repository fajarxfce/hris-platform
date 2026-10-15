import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { OnboardingSchedule, OnboardingScheduleId } from "../entities/onboarding-schedule";
import type {
  OnboardingScheduleChange,
  OnboardingScheduleCreation,
} from "../entities/onboarding-schedule-command";
import type { OnboardingScheduleReview } from "../entities/onboarding-schedule-review";
import type { OnboardingScheduleSelection } from "../entities/onboarding-schedule-selection";
export interface OnboardingScheduleRepository {
  review(
    company: CompanyId,
    employee: EmployeeId,
    selection: OnboardingScheduleSelection,
    signal: AbortSignal,
  ): Promise<Result<OnboardingScheduleReview>>;
  list(
    company: CompanyId,
    employee: EmployeeId,
    signal: AbortSignal,
  ): Promise<Result<readonly OnboardingSchedule[]>>;
  get(
    company: CompanyId,
    employee: EmployeeId,
    id: OnboardingScheduleId,
    signal: AbortSignal,
  ): Promise<Result<OnboardingSchedule>>;
  schedule(
    company: CompanyId,
    operation: OperationId,
    input: OnboardingScheduleCreation,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  cancel(
    company: CompanyId,
    operation: OperationId,
    input: OnboardingScheduleChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  recover(
    company: CompanyId,
    operation: OperationId,
    input: OnboardingScheduleChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
