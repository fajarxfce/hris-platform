import type { CompanyMembership } from "../../../identity/domain/entities/session";
import type { Employee } from "./employee";

/** Observed command context, not a reservation of employment or destination access. */
export type EmploymentTransferReview = Readonly<{
  employee: Employee;
  sourceCompany: CompanyMembership;
  targetCompany: CompanyMembership;
  sourceDate: string;
  targetDate: string;
  evaluatedAt: string;
  targetPermissions: readonly string[];
}>;
