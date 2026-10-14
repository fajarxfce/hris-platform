import type { PayrollPolicyChangeDto } from "../models/payroll-policy-change-dto";
import type {
  PayrollIncomeTaxRulesDto,
  PayrollPolicyReceiptDto,
  PayrollPolicyReviewDto,
} from "../models/payroll-policy-dto";

export interface PayrollPolicyDataSource {
  review(
    company: string,
    month: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<PayrollPolicyReviewDto>;
  rules(company: string, signal: AbortSignal): Promise<PayrollIncomeTaxRulesDto>;
  save(
    company: string,
    operation: string,
    change: PayrollPolicyChangeDto,
    signal: AbortSignal,
  ): Promise<PayrollPolicyReceiptDto>;
}
