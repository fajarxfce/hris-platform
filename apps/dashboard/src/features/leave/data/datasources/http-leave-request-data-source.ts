import { binaryResponseDto } from "../../../../core/data/http/binary-response-dto";
import type { HttpClient } from "../../../../core/data/http/http-client";
import type { LeaveAttachmentDto } from "../models/leave-attachment-dto";
import { leaveReceiptDto } from "../models/leave-receipt-dto";
import type {
  LeaveRequestChangeDto,
  LeaveRequestDecisionDto,
} from "../models/leave-request-change-dto";
import { leaveRequestDetailsDto } from "../models/leave-request-details-dto";
import { leaveRequestPageDto } from "../models/leave-request-summary-dto";
import type { LeaveRequestDataSource } from "./leave-request-data-source";

export class HttpLeaveRequestDataSource implements LeaveRequestDataSource {
  constructor(private readonly http: HttpClient) {}
  async downloadAttachment(
    company: string,
    request: string,
    attachment: Pick<LeaveAttachmentDto, "revisionId" | "mediaType" | "size" | "sha256">,
    signal: AbortSignal,
  ) {
    return binaryResponseDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/leave/requests/${request}/attachments/${attachment.revisionId}/content`,
          timeoutMilliseconds: 60_000,
          response: {
            type: "binary",
            mediaType: attachment.mediaType,
            byteLength: attachment.size,
            etag: `"${attachment.revisionId}-${attachment.sha256}"`,
          },
        },
        signal,
      ),
    );
  }
  async decide(
    company: string,
    id: string,
    operation: string,
    body: LeaveRequestDecisionDto,
    signal: AbortSignal,
  ) {
    return leaveReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/leave/requests/${id}/decisions`,
          method: "POST",
          operationId: operation,
          body,
        },
        signal,
      ),
    );
  }
  async withdraw(
    company: string,
    id: string,
    operation: string,
    body: LeaveRequestChangeDto,
    signal: AbortSignal,
  ) {
    return leaveReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/leave/requests/${id}/withdraw`,
          method: "POST",
          operationId: operation,
          body,
        },
        signal,
      ),
    );
  }
  async requestCancellation(
    company: string,
    id: string,
    operation: string,
    body: LeaveRequestChangeDto,
    signal: AbortSignal,
  ) {
    return leaveReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/leave/requests/${id}/cancellation`,
          method: "POST",
          operationId: operation,
          body,
        },
        signal,
      ),
    );
  }
  async list(
    company: string,
    employee: string | null,
    status: string | null,
    after: string | null,
    signal: AbortSignal,
  ) {
    const query = new URLSearchParams({ limit: "20" });
    if (employee !== null) query.set("employeeId", employee);
    if (status !== null) query.set("status", status);
    if (after !== null) query.set("after", after);
    return leaveRequestPageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/leave/requests?${query}` },
        signal,
      ),
    );
  }
  async get(company: string, id: string, historyAfter: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ historyLimit: "20" });
    if (historyAfter !== null) query.set("historyAfter", historyAfter);
    return leaveRequestDetailsDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/leave/requests/${id}?${query}` },
        signal,
      ),
    );
  }
}
