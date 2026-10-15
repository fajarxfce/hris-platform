import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { LeaveCarryExpiryId, LeaveCarryExpiryPage } from "../entities/leave-carry-expiry";
import type { LeaveCarryExpiryDetails } from "../entities/leave-carry-expiry-details";
import type { LeaveCarryExpiryResume } from "../entities/leave-carry-expiry-resume";
export interface LeaveCarryExpiryRepository {
  list(
    company: CompanyId,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<LeaveCarryExpiryPage>>;
  get(
    company: CompanyId,
    id: LeaveCarryExpiryId,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<LeaveCarryExpiryDetails>>;
  resume(
    operation: OperationId,
    input: LeaveCarryExpiryResume,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
