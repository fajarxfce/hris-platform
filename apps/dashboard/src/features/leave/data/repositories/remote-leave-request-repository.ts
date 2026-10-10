import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveRequestId, LeaveRequestQuery } from "../../domain/entities/leave-request";
import type { LeaveRequestRepository } from "../../domain/repositories/leave-request-repository";
import type { LeaveRequestDataSource } from "../datasources/leave-request-data-source";
import { toLeaveRequestDetails } from "../mappers/leave-request-details-mapper";
import { toLeaveRequestPage } from "../mappers/leave-request-mapper";

export class RemoteLeaveRequestRepository implements LeaveRequestRepository {
  constructor(private readonly source: LeaveRequestDataSource) {}
  list(company: CompanyId, query: LeaveRequestQuery, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLeaveRequestPage(
        await this.source.list(company, query.employeeId, query.status, query.after, signal),
        company,
        query,
      ),
    );
  }
  get(company: CompanyId, id: LeaveRequestId, historyAfter: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () => {
      const request = toLeaveRequestDetails(
        await this.source.get(company, id, historyAfter, signal),
        company,
        historyAfter,
      );
      if (request.id !== id) throw new InvalidHttpResponseError();
      return request;
    });
  }
}
