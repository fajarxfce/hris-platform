import type { FileRepository } from "../../../../core/domain/files/file-repository";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpensePaymentReview } from "../entities/expense-payment-review";
import { canReadExpensePayments } from "../policies/expense-payment-read-policy";
import type { ExpensePaymentRepository } from "../repositories/expense-payment-repository";
export class DownloadExpensePayment {
  constructor(
    private readonly payments: ExpensePaymentRepository,
    private readonly files: Pick<FileRepository, "downloadText">,
  ) {}
  async execute(access: CompanyAccess, review: ExpensePaymentReview, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadExpensePayments(access.permissions) || review.batch.companyId !== access.companyId)
      return failed("access_denied");
    if (!review.availableActions.includes("EXPORT"))
      return failed("expense_payment_instructions_unavailable");
    const worksheet = await this.payments.worksheet(access.companyId, review.batch.id, signal);
    signal.throwIfAborted();
    if (!worksheet.ok) return worksheet;
    const result = await this.files.downloadText(
      {
        name: `expense-payments-${review.batch.id}.csv`,
        mediaType: "text/csv",
        text: worksheet.value,
      },
      signal,
    );
    signal.throwIfAborted();
    return result;
  }
}
