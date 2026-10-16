import type { RoleTemplateSelection } from "./role-template-selection";

export type CompanyMembershipChange = Readonly<{
  accountId: string;
  expectedVersion: number;
  active: boolean;
  permissions: readonly string[];
  roleTemplates: readonly RoleTemplateSelection[];
  reason: string;
}>;
