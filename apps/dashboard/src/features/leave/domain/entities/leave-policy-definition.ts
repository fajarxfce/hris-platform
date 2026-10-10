import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeavePolicyTerms } from "./leave-policy-terms";

export type LeavePolicyId = string & { readonly leavePolicyId: unique symbol };
export type LeavePolicyDefinition = Readonly<
  LeavePolicyTerms & {
    id: LeavePolicyId;
    companyId: CompanyId;
    code: string;
    effectiveFrom: string;
    active: boolean;
    version: number;
  }
>;
export type LeavePolicyPage = Readonly<{
  items: readonly LeavePolicyDefinition[];
  nextCursor: string | null;
}>;
export type LeavePolicyQuery = Readonly<{ active: string | null; after: string | null }>;
