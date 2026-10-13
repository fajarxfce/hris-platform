import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import { binaryFilePartBytes } from "../../../../core/domain/files/binary-file";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { ExpenseClaimId } from "../../domain/entities/expense-claim";
import type { ExpenseClaimSearch } from "../../domain/entities/expense-claim-search";
import type { ExpenseDecisionCommand } from "../../domain/entities/expense-decision-command";
import type { ExpenseReceipt } from "../../domain/entities/expense-receipt";
import type { ExpenseSubmissionId } from "../../domain/entities/expense-submission-summary";
import type { ExpenseClaimRepository } from "../../domain/repositories/expense-claim-repository";
import type { ExpenseClaimDataSource } from "../datasources/expense-claim-data-source";
import { toExpenseClaimPage } from "../mappers/expense-claim-mapper";
import { toExpenseClaimReview } from "../mappers/expense-claim-review-mapper";
import { toExpenseSubmission } from "../mappers/expense-submission-mapper";
export class RemoteExpenseClaimRepository implements ExpenseClaimRepository {
  constructor(private readonly source: ExpenseClaimDataSource) {}
  decide(
    company: CompanyId,
    operation: OperationId,
    change: ExpenseDecisionCommand,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.decide(
        company,
        change.id,
        operation,
        {
          expectedVersion: change.claimVersion,
          expectedApprovalVersion: change.approvalVersion,
          decision: change.decision,
          reason: change.reason,
          acknowledgeDuplicates: change.acknowledgeDuplicates,
        },
        signal,
      );
      if (
        receipt.id.toLowerCase() !== change.claimId.toLowerCase() ||
        receipt.version !== change.claimVersion + 1
      )
        throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
  list(company: CompanyId, search: ExpenseClaimSearch, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toExpenseClaimPage(
        await this.source.list(
          company,
          search.from,
          search.until,
          search.status,
          search.employmentId,
          search.after,
          signal,
        ),
        company,
        search,
      ),
    );
  }
  review(company: CompanyId, id: ExpenseClaimId, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toExpenseClaimReview(
        await this.source.review(company, id, after, signal),
        company,
        id,
        after,
      ),
    );
  }
  submission(company: CompanyId, id: ExpenseSubmissionId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toExpenseSubmission(await this.source.submission(company, id, signal), company, id),
    );
  }
  downloadReceipt(
    company: CompanyId,
    id: ExpenseSubmissionId,
    receipt: ExpenseReceipt,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const content = await this.source.downloadReceipt(
        company,
        id,
        {
          revisionId: receipt.revisionId,
          size: receipt.size,
          mediaType: receipt.mediaType,
          sha256: receipt.sha256,
        },
        signal,
      );
      if (
        content.byteLength !== receipt.size ||
        content.mediaType !== receipt.mediaType ||
        content.etag !== `"${receipt.revisionId}-${receipt.sha256}"` ||
        content.parts.some(
          (part) => part.byteLength < 1 || part.byteLength > binaryFilePartBytes,
        ) ||
        content.parts.reduce((sum, part) => sum + part.byteLength, 0) !== receipt.size
      )
        throw new InvalidHttpResponseError();
      return Object.freeze({
        byteLength: content.byteLength,
        parts: Object.freeze([...content.parts]),
      });
    });
  }
}
