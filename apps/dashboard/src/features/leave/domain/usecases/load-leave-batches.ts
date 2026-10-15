import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadLeaveBatches } from "../policies/leave-batch-policy";
import type { LeaveBatchRepository } from "../repositories/leave-batch-repository";
export class LoadLeaveBatches {
  constructor(private readonly batches: LeaveBatchRepository) {}
  execute(access: CompanyAccess, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadLeaveBatches(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (after !== null && !isUuid(after)) return Promise.resolve(failed("invalid_page"));
    return this.batches.list(access.companyId, after?.toLowerCase() ?? null, signal);
  }
}
