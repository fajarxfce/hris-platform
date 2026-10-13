import { binaryResponseDto } from "../../../../core/data/http/binary-response-dto";
import type { HttpClient } from "../../../../core/data/http/http-client";
import { expenseClaimPageDto, expenseClaimReviewDto } from "../models/expense-claim-dto";
import {
  type ExpenseReceiptMetadataDto,
  expenseSubmissionDto,
} from "../models/expense-submission-dto";
import type { ExpenseClaimDataSource } from "./expense-claim-data-source";
export class HttpExpenseClaimDataSource implements ExpenseClaimDataSource {
  constructor(private readonly http: HttpClient) {}
  async list(
    company: string,
    from: string,
    until: string,
    status: string | null,
    employment: string | null,
    after: string | null,
    signal: AbortSignal,
  ) {
    const query = new URLSearchParams({ from, until, limit: "20" });
    if (status !== null) query.set("status", status);
    if (employment !== null) query.set("employmentId", employment);
    if (after !== null) query.set("after", after);
    return expenseClaimPageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/expenses/claims?${query}` },
        signal,
      ),
    );
  }
  async review(company: string, id: string, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ historyLimit: "20", submissionsLimit: "20" });
    if (after !== null) query.set("historyAfter", after);
    return expenseClaimReviewDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/expenses/claims/${id}/review?${query}` },
        signal,
      ),
    );
  }
  async submission(company: string, id: string, signal: AbortSignal) {
    return expenseSubmissionDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/expenses/submissions/${id}` },
        signal,
      ),
    );
  }
  async downloadReceipt(
    company: string,
    id: string,
    receipt: Pick<ExpenseReceiptMetadataDto, "revisionId" | "mediaType" | "size" | "sha256">,
    signal: AbortSignal,
  ) {
    return binaryResponseDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/expenses/submissions/${id}/receipts/${receipt.revisionId}/content`,
          timeoutMilliseconds: 60_000,
          response: {
            type: "binary",
            mediaType: receipt.mediaType,
            byteLength: receipt.size,
            etag: `"${receipt.revisionId}-${receipt.sha256}"`,
          },
        },
        signal,
      ),
    );
  }
}
