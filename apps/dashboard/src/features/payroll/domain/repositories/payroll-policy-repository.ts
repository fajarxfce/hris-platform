import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { IncomeTaxRule } from "../entities/income-tax-rule";
import type { PayrollPolicyChange } from "../entities/payroll-policy-change";
import type { PayrollPolicyReview } from "../entities/payroll-policy-review";

export interface PayrollPolicyRepository {
  review(
    company: CompanyId,
    month: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<PayrollPolicyReview>>;
  rules(company: CompanyId, signal: AbortSignal): Promise<Result<readonly IncomeTaxRule[]>>;
  save(
    company: CompanyId,
    operation: OperationId,
    change: PayrollPolicyChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
