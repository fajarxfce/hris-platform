import type { CompanyId } from "../../../../core/domain/identifiers";
import type { EmployeeId } from "./employee";

export type EmploymentTransfer = Readonly<{
  id: string;
  sourceCompanyId: CompanyId;
  sourceEmploymentId: EmployeeId;
  targetCompanyId: CompanyId;
  targetEmploymentId: EmployeeId;
  sourceVersion: number;
  effectiveDate: string;
  reason: string;
  recordedAt: string;
}>;
