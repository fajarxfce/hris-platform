import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { ApprovalReassignment } from "../../domain/entities/approval-reassignment";
import type { ApprovalId } from "../../domain/entities/approval-request";
import type { ApprovalRepository } from "../../domain/repositories/approval-repository";
import type { ApprovalDataSource } from "../datasources/approval-data-source";
import { toApprovalInbox, toApprovalRequest } from "../mappers/approval-request-mapper";

export class RemoteApprovalRepository implements ApprovalRepository {
  constructor(private readonly source: ApprovalDataSource) {}
  inbox(company: CompanyId, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toApprovalInbox(await this.source.inbox(company, after, signal), company, after),
    );
  }
  get(company: CompanyId, id: ApprovalId, signal: AbortSignal) {
    return safeHttpCall(signal, async () => {
      const request = toApprovalRequest(await this.source.get(company, id, signal), company);
      if (request.id !== id) throw new InvalidHttpResponseError();
      return request;
    });
  }
  reassign(
    company: CompanyId,
    operation: OperationId,
    change: ApprovalReassignment,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.reassign(
        company,
        change.id,
        operation,
        {
          version: change.version,
          assignees: [...change.assignees],
          reason: change.reason,
        },
        signal,
      );
      if (receipt.id.toLowerCase() !== change.id || receipt.version !== change.version + 1)
        throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
}
