import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { OvertimeActualSubmission } from "../entities/overtime-actual-submission";
import type { OvertimeDecision } from "../entities/overtime-decision";
import type { OvertimePage } from "../entities/overtime-page";
import type { OvertimePlan } from "../entities/overtime-plan";
import type { OvertimePlanningReview } from "../entities/overtime-planning-review";
import type { OvertimePlanningSearch } from "../entities/overtime-planning-search";
import type { OvertimeRequestId } from "../entities/overtime-request";
import type { OvertimeRequestChange } from "../entities/overtime-request-change";
import type { OvertimeRequestDetails } from "../entities/overtime-request-details";
import type { OvertimeSearch } from "../entities/overtime-search";
import type { WorkforceEmployeePage } from "../entities/workforce-employee-page";

export interface OvertimeRepository {
  planningEmployees(
    company: CompanyId,
    search: OvertimePlanningSearch,
    signal: AbortSignal,
  ): Promise<Result<WorkforceEmployeePage>>;
  planningReview(
    company: CompanyId,
    employeeId: EmployeeId,
    workDate: string,
    signal: AbortSignal,
  ): Promise<Result<OvertimePlanningReview>>;
  plan(
    company: CompanyId,
    operation: OperationId,
    plan: OvertimePlan,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  submitActual(
    company: CompanyId,
    operation: OperationId,
    change: OvertimeActualSubmission,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  decide(
    company: CompanyId,
    operation: OperationId,
    change: OvertimeDecision,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  withdraw(
    company: CompanyId,
    operation: OperationId,
    change: OvertimeRequestChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  list(
    company: CompanyId,
    search: OvertimeSearch,
    signal: AbortSignal,
  ): Promise<Result<OvertimePage>>;
  get(
    company: CompanyId,
    id: OvertimeRequestId,
    historyAfter: string | null,
    signal: AbortSignal,
  ): Promise<Result<OvertimeRequestDetails>>;
}
