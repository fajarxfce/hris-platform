import type { RoleTemplateSelection } from "./role-template-selection";

export type CompanyMembershipChange = Readonly<{
  accountId: string;
  /** Null creates a membership; an observed version updates an existing one. */
  expectedVersion: number | null;
  active: boolean;
  permissions: readonly string[];
  roleTemplates: readonly RoleTemplateSelection[];
  reason: string;
}>;
