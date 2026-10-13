import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ExpensePolicyTerms } from "./expense-policy-terms";

export type ExpensePolicyId = string & { readonly expensePolicyId: unique symbol };
export type ExpensePolicyDefinition = Readonly<
  ExpensePolicyTerms & {
    id: ExpensePolicyId;
    companyId: CompanyId;
    code: string;
    effectiveFrom: string;
    active: boolean;
    version: number;
  }
>;
export type ExpensePolicyPage = Readonly<{
  items: readonly ExpensePolicyDefinition[];
  nextCursor: string | null;
}>;
export type ExpensePolicyQuery = Readonly<{ active: string | null; after: string | null }>;
