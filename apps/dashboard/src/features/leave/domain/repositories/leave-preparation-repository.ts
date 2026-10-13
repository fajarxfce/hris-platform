import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { LeaveEvidenceOptions } from "../entities/leave-evidence-option";
import type { LeavePreparationDirectory } from "../entities/leave-preparation-employee";
import type { LeaveRequestPreview } from "../entities/leave-request-preview";
import type { LeaveRequestSelection } from "../entities/leave-request-selection";
import type { LeaveRequestTypes } from "../entities/leave-request-type";

export interface LeavePreparationRepository {
  types(
    company: CompanyId,
    asOf: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<LeaveRequestTypes>>;
  employees(
    company: CompanyId,
    query: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<LeavePreparationDirectory>>;
  evidence(
    company: CompanyId,
    employee: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<LeaveEvidenceOptions>>;
  preview(
    company: CompanyId,
    selection: LeaveRequestSelection,
    signal: AbortSignal,
  ): Promise<Result<LeaveRequestPreview>>;
}
