import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { PayrollPayableSearch } from "../../domain/entities/payroll-payable-search";
import type { PayrollPaymentBatchId } from "../../domain/entities/payroll-payment-batch";
import type { PayrollPaymentPreparationCommand } from "../../domain/entities/payroll-payment-preparation-command";
import type { PayrollPaymentReconciliationCommand } from "../../domain/entities/payroll-payment-reconciliation-command";
import type { PayrollPaymentSearch } from "../../domain/entities/payroll-payment-search";
import type { PayrollPaymentTransitionCommand } from "../../domain/entities/payroll-payment-transition-command";
import type { PayrollPaymentRepository } from "../../domain/repositories/payroll-payment-repository";
import type { PayrollPaymentDataSource } from "../datasources/payroll-payment-data-source";
import { toPayrollPayablePage } from "../mappers/payroll-payable-mapper";
import { toPayrollPaymentPage } from "../mappers/payroll-payment-mapper";
import { toPayrollPaymentReview } from "../mappers/payroll-payment-review-mapper";
export class RemotePayrollPaymentRepository implements PayrollPaymentRepository {
  constructor(private readonly source: PayrollPaymentDataSource) {}
  payables(company: CompanyId, search: PayrollPayableSearch, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollPayablePage(
        await this.source.payables(company, search.from, search.until, search.after, signal),
        company,
        search,
      ),
    );
  }
  prepare(
    company: CompanyId,
    operation: OperationId,
    command: PayrollPaymentPreparationCommand,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.prepare(
        company,
        command.id,
        operation,
        {
          title: command.title,
          reason: command.reason,
          items: command.items.map((item) => ({
            id: item.id,
            assessmentId: item.assessmentId,
            destination: { ...item.destination },
          })),
        },
        signal,
      );
      if (receipt.id.toLowerCase() !== command.id || receipt.version !== 0)
        throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
  reconcile(
    company: CompanyId,
    operation: OperationId,
    command: PayrollPaymentReconciliationCommand,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.reconcile(
        company,
        command.id,
        operation,
        {
          expectedVersion: command.version,
          results: command.results.map((result) => ({ ...result })),
          reason: command.reason,
        },
        signal,
      );
      if (receipt.id.toLowerCase() !== command.id || receipt.version !== command.version + 1)
        throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
  transition(
    company: CompanyId,
    operation: OperationId,
    command: PayrollPaymentTransitionCommand,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.transition(
        company,
        command.id,
        operation,
        command.transition === "RELEASE" ? "release" : "cancel",
        { expectedVersion: command.version, reason: command.reason },
        signal,
      );
      if (receipt.id.toLowerCase() !== command.id || receipt.version !== command.version + 1)
        throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
  list(company: CompanyId, search: PayrollPaymentSearch, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollPaymentPage(
        await this.source.list(
          company,
          search.from,
          search.until,
          search.status,
          search.after,
          signal,
        ),
        company,
        search,
      ),
    );
  }
  review(company: CompanyId, id: PayrollPaymentBatchId, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollPaymentReview(
        await this.source.review(company, id, after, signal),
        company,
        id,
        after,
      ),
    );
  }
  worksheet(company: CompanyId, id: PayrollPaymentBatchId, signal: AbortSignal) {
    return safeHttpCall(signal, () => this.source.worksheet(company, id, signal));
  }
}
