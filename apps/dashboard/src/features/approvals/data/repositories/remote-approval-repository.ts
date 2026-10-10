import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
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
}
