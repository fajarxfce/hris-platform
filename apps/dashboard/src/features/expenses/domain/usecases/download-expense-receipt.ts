import { maximumBinaryFileBytes } from "../../../../core/domain/files/binary-file";
import type { FileRepository } from "../../../../core/domain/files/file-repository";
import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseSubmission } from "../entities/expense-submission";
import { canReadExpenseSubmissions } from "../policies/expense-claim-read-policy";
import type { ExpenseClaimRepository } from "../repositories/expense-claim-repository";
export class DownloadExpenseReceipt {
  constructor(
    private readonly claims: ExpenseClaimRepository,
    private readonly files: Pick<FileRepository, "downloadBinary">,
  ) {}
  async execute(
    access: CompanyAccess,
    submission: ExpenseSubmission,
    revision: string,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (access.companyId !== submission.companyId || !canReadExpenseSubmissions(access.permissions))
      return failed("access_denied");
    if (!isUuid(submission.id) || !isUuid(revision)) return failed("expense_receipt_not_found");
    const receipt = submission.lines
      .flatMap((line) => line.receipts)
      .find((item) => item.revisionId === revision.toLowerCase());
    if (!receipt) return failed("expense_receipt_not_found");
    if (
      !Number.isSafeInteger(receipt.size) ||
      receipt.size < 1 ||
      receipt.size > maximumBinaryFileBytes ||
      !["application/pdf", "image/jpeg", "image/png"].includes(receipt.mediaType) ||
      !/^[0-9a-f]{64}$/u.test(receipt.sha256) ||
      receipt.fileName.trim().length === 0 ||
      receipt.fileName.length > 180 ||
      /[\p{Cc}/\\]/u.test(receipt.fileName)
    )
      return failed("expense_receipt_unavailable");
    const content = await this.claims.downloadReceipt(
      access.companyId,
      submission.id,
      receipt,
      signal,
    );
    signal.throwIfAborted();
    if (!content.ok) return content;
    return this.files.downloadBinary(
      Object.freeze({ ...content.value, name: receipt.fileName, mediaType: receipt.mediaType }),
      signal,
    );
  }
}
