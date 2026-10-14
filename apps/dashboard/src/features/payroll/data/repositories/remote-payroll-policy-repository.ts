import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { PayrollPolicyChange } from "../../domain/entities/payroll-policy-change";
import type { PayrollPolicyRepository } from "../../domain/repositories/payroll-policy-repository";
import type { PayrollPolicyDataSource } from "../datasources/payroll-policy-data-source";
import { toIncomeTaxRules, toPayrollPolicyReview } from "../mappers/payroll-policy-mapper";

export class RemotePayrollPolicyRepository implements PayrollPolicyRepository {
  constructor(private readonly source: PayrollPolicyDataSource) {}
  review(company: CompanyId, month: string, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollPolicyReview(
        await this.source.review(company, month, after, signal),
        company,
        month,
        after,
      ),
    );
  }
  rules(company: CompanyId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toIncomeTaxRules(await this.source.rules(company, signal)),
    );
  }
  save(
    company: CompanyId,
    operation: OperationId,
    change: PayrollPolicyChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.save(
        company,
        operation,
        {
          effectiveFrom: change.effectiveFrom,
          effectiveUntil: change.effectiveUntil,
          incomeTaxRuleId: change.incomeTaxRuleId,
          insuranceRuleId: change.insuranceRuleId,
          minimumMonthlyWage: change.minimumMonthlyWage,
          healthWageCap: change.healthWageCap,
          pensionWageCap: change.pensionWageCap,
          contributionRounding: change.contributionRounding,
          reviewReferences: [...change.reviewReferences],
          expectedVersion: change.expectedVersion,
          reason: change.reason,
        },
        signal,
      );
      if (
        receipt.id.toLowerCase() !== company ||
        receipt.version !== (change.expectedVersion ?? -1) + 1
      )
        throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: receipt.version });
    });
  }
}
