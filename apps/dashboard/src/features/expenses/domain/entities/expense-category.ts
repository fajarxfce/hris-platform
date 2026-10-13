import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ExpensePolicyTerms } from "./expense-policy-terms";
export type ExpenseCategory = Readonly<
  ExpensePolicyTerms & {
    id: string;
    companyId: CompanyId;
    code: string;
    effectiveFrom: string;
    version: number;
    appliedRevision: number;
  }
>;
export type ExpenseCategoryPage = Readonly<{
  items: readonly ExpenseCategory[];
  nextCursor: string | null;
}>;
