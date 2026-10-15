import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveBatchId } from "../entities/leave-batch";
import { canReadLeaveBatches, isLeaveBatchResultCursor } from "../policies/leave-batch-policy";
import type { LeaveBatchRepository } from "../repositories/leave-batch-repository";
export class LoadLeaveBatch {
  constructor(private readonly batches: LeaveBatchRepository) {}
  execute(access: CompanyAccess, id: string, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadLeaveBatches(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("leave_batch_not_found"));
    if (!isLeaveBatchResultCursor(after)) return Promise.resolve(failed("invalid_page"));
    return this.batches.get(access.companyId, id.toLowerCase() as LeaveBatchId, after, signal);
  }
}
