import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import { binaryFilePartBytes } from "../../../../core/domain/files/binary-file";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { LeaveAttachment } from "../../domain/entities/leave-attachment";
import type { LeaveRequestId, LeaveRequestQuery } from "../../domain/entities/leave-request";
import type {
  LeaveRequestChange,
  LeaveRequestDecision,
} from "../../domain/entities/leave-request-action";
import type { LeaveRequestRepository } from "../../domain/repositories/leave-request-repository";
import type { LeaveRequestDataSource } from "../datasources/leave-request-data-source";
import { toLeaveReceipt } from "../mappers/leave-receipt-mapper";
import { toLeaveRequestDetails } from "../mappers/leave-request-details-mapper";
import { toLeaveRequestPage } from "../mappers/leave-request-mapper";

export class RemoteLeaveRequestRepository implements LeaveRequestRepository {
  constructor(private readonly source: LeaveRequestDataSource) {}
  downloadAttachment(
    company: CompanyId,
    request: LeaveRequestId,
    attachment: LeaveAttachment,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const content = await this.source.downloadAttachment(
        company,
        request,
        {
          revisionId: attachment.revisionId.toLowerCase(),
          mediaType: attachment.mediaType,
          size: attachment.size,
          sha256: attachment.sha256,
        },
        signal,
      );
      if (
        content.byteLength !== attachment.size ||
        content.mediaType !== attachment.mediaType ||
        content.etag !== `"${attachment.revisionId.toLowerCase()}-${attachment.sha256}"` ||
        content.parts.some(
          (part) => part.byteLength < 1 || part.byteLength > binaryFilePartBytes,
        ) ||
        content.parts.reduce((size, part) => size + part.byteLength, 0) !== attachment.size
      )
        throw new InvalidHttpResponseError();
      return Object.freeze({
        byteLength: content.byteLength,
        parts: Object.freeze([...content.parts]),
      });
    });
  }
  decide(
    company: CompanyId,
    operation: OperationId,
    command: LeaveRequestDecision,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toLeaveReceipt(
        await this.source.decide(
          company,
          command.id,
          operation,
          { version: command.version, decision: command.decision, reason: command.reason },
          signal,
        ),
        command,
      ),
    );
  }
  withdraw(
    company: CompanyId,
    operation: OperationId,
    command: LeaveRequestChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toLeaveReceipt(
        await this.source.withdraw(
          company,
          command.id,
          operation,
          { version: command.version, reason: command.reason },
          signal,
        ),
        command,
      ),
    );
  }
  requestCancellation(
    company: CompanyId,
    operation: OperationId,
    command: LeaveRequestChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toLeaveReceipt(
        await this.source.requestCancellation(
          company,
          command.id,
          operation,
          { version: command.version, reason: command.reason },
          signal,
        ),
        command,
      ),
    );
  }
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
