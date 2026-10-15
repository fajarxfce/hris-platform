import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveBatchSelection } from "../entities/leave-batch-review";
import { canRunLeaveBatch, normalizeLeaveBatchSelection } from "../policies/leave-batch-policy";
import type { LeaveBatchRepository } from "../repositories/leave-batch-repository";
export class ReviewLeaveBatch {
  constructor(private readonly batches: LeaveBatchRepository) {}
  execute(access: CompanyAccess, input: LeaveBatchSelection, signal: AbortSignal) {
    signal.throwIfAborted();
    const selected = normalizeLeaveBatchSelection(input);
    if (!selected.ok) return Promise.resolve(selected);
    if (!canRunLeaveBatch(access.permissions, selected.value.kind))
      return Promise.resolve(failed("access_denied"));
    return this.batches.review(access.companyId, selected.value, signal);
  }
}
