import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalId } from "../entities/approval-request";
import type { ApprovalHistoryRepository } from "../repositories/approval-history-repository";
export class LoadApprovalHistory {
  constructor(private readonly history: ApprovalHistoryRepository) {}
  execute(
    access: CompanyAccess,
    id: string,
    after: number | null,
    expectedVersion: number | null,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!isUuid(id)) return Promise.resolve(failed("approval_not_found"));
    if (
      (after !== null && (!Number.isSafeInteger(after) || after < 1 || expectedVersion === null)) ||
      (expectedVersion !== null && (!Number.isSafeInteger(expectedVersion) || expectedVersion < 0))
    )
      return Promise.resolve(failed("invalid_approval_history_query"));
    return this.history.get(
      access.companyId,
      id.toLowerCase() as ApprovalId,
      after,
      expectedVersion,
      signal,
    );
  }
}
