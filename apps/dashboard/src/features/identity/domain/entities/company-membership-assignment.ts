import type { CompanyMembershipChange } from "./company-membership-change";

export type CompanyMembershipAssignment = Omit<
  CompanyMembershipChange,
  "expectedVersion" | "active"
>;
