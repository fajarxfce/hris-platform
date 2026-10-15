import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { type LeaveBatchKind, leaveBatchKinds } from "../entities/leave-batch";
import { canRunLeaveBatch } from "../policies/leave-batch-policy";
import type { LeaveBatchRepository } from "../repositories/leave-batch-repository";
export class LoadLeaveBatchTypes {
  constructor(private readonly batches: LeaveBatchRepository) {}
  execute(access: CompanyAccess, kind: LeaveBatchKind, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!leaveBatchKinds.includes(kind)) return Promise.resolve(failed("invalid_leave_batch"));
    if (!canRunLeaveBatch(access.permissions, kind))
      return Promise.resolve(failed("access_denied"));
    if (after !== null && !/^[A-Z][A-Z0-9_-]{0,31}$/u.test(after))
      return Promise.resolve(failed("invalid_page"));
    return this.batches.types(access.companyId, kind, after, signal);
  }
}
