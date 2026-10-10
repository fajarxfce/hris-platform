import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveEmployeeReference } from "./leave-employee-reference";
import type { LeavePolicyPage } from "./leave-policy-definition";

export type LeaveAdjustmentCatalog = Readonly<{
  companyId: CompanyId;
  employee: LeaveEmployeeReference;
  year: number;
  policies: LeavePolicyPage;
}>;
