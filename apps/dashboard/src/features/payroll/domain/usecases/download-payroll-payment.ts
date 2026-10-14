import type { FileRepository } from "../../../../core/domain/files/file-repository";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPaymentReview } from "../entities/payroll-payment-review";
import { canReadPayrollPayments } from "../policies/payroll-payment-read-policy";
import type { PayrollPaymentRepository } from "../repositories/payroll-payment-repository";
export class DownloadPayrollPayment {
  constructor(
    private readonly payments: PayrollPaymentRepository,
    private readonly files: Pick<FileRepository, "downloadText">,
  ) {}
  async execute(access: CompanyAccess, review: PayrollPaymentReview, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadPayrollPayments(access.permissions) || review.batch.companyId !== access.companyId)
      return failed("access_denied");
    if (!review.availableActions.includes("EXPORT"))
      return failed("payroll_payment_instructions_unavailable");
    const worksheet = await this.payments.worksheet(access.companyId, review.batch.id, signal);
    signal.throwIfAborted();
    if (!worksheet.ok) return worksheet;
    const result = await this.files.downloadText(
      {
        name: `payroll-payments-${review.batch.id}.csv`,
        mediaType: "text/csv",
        text: worksheet.value,
      },
      signal,
    );
    signal.throwIfAborted();
    return result;
  }
}
