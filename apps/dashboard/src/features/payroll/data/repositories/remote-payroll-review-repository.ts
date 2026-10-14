import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type {
  PayrollReviewDecision,
  PayrollReviewSubmission,
  PayrollReviewWithdrawal,
} from "../../domain/entities/payroll-review-command";
import type { PayrollReviewRepository } from "../../domain/repositories/payroll-review-repository";
import type { PayrollReviewDataSource } from "../datasources/payroll-review-data-source";
import {
  toPayrollReviewDetails,
  toPayrollReviewPreparation,
  toPayrollReviews,
} from "../mappers/payroll-review-mapper";
export class RemotePayrollReviewRepository implements PayrollReviewRepository {
  constructor(private readonly source: PayrollReviewDataSource) {}
  prepare(company: CompanyId, run: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollReviewPreparation(await this.source.prepare(company, run, signal), company, run),
    );
  }
  list(company: CompanyId, run: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollReviews(await this.source.list(company, run, signal), company, run),
    );
  }
  get(company: CompanyId, id: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollReviewDetails(await this.source.get(company, id, signal), company, id),
    );
  }
  submit(
    company: CompanyId,
    run: string,
    operation: OperationId,
    input: PayrollReviewSubmission,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.submit(company, run, operation, input, signal);
      if (receipt.id !== input.id || receipt.version !== 0) throw new InvalidHttpResponseError();
      return Object.freeze({ ...receipt });
    });
  }
  decide(
    company: CompanyId,
    id: string,
    operation: OperationId,
    input: PayrollReviewDecision,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.decide(company, id, operation, input, signal);
      if (receipt.id !== id || receipt.version !== input.expectedVersion + 1)
        throw new InvalidHttpResponseError();
      return Object.freeze({ ...receipt });
    });
  }
  withdraw(
    company: CompanyId,
    id: string,
    operation: OperationId,
    input: PayrollReviewWithdrawal,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.withdraw(company, id, operation, input, signal);
      if (receipt.id !== id || receipt.version !== input.expectedVersion + 1)
        throw new InvalidHttpResponseError();
      return Object.freeze({ ...receipt });
    });
  }
}
