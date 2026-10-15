import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { PayrollPaymentRecoveryCommand } from "../../domain/entities/payroll-payment-recovery-command";
import type { PayrollPaymentRecoveryRepository } from "../../domain/repositories/payroll-payment-recovery-repository";
import type { PayrollPaymentRecoveryDataSource } from "../datasources/payroll-payment-recovery-data-source";
import { toPayrollPaymentRecoveryReview } from "../mappers/payroll-payment-recovery-mapper";

export class RemotePayrollPaymentRecoveryRepository implements PayrollPaymentRecoveryRepository {
  constructor(private readonly source: PayrollPaymentRecoveryDataSource) {}
  review(company: CompanyId, assessment: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollPaymentRecoveryReview(
        await this.source.review(company, assessment, signal),
        company,
        assessment,
      ),
    );
  }
  confirm(
    company: CompanyId,
    operation: OperationId,
    command: PayrollPaymentRecoveryCommand,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.confirm(
        company,
        command.assessmentId,
        command.id,
        operation,
        {
          assessmentRevision: command.assessmentRevision,
          paymentVersion: command.paymentVersion,
          amount: command.amount,
          transactionReference: command.transactionReference,
          occurredAt: command.occurredAt,
          reason: command.reason,
        },
        signal,
      );
      if (receipt.id.toLowerCase() !== command.id || receipt.version !== 0)
        throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
}
