import type { CompanyMemberGrant } from "../../domain/entities/company-member";
export type SelectedCompanyRole = CompanyMemberGrant["roleTemplates"][number] & {
  retainApplied: boolean;
};
export type CompanyMembershipFormValues = {
  active: boolean;
  permissions: string[];
  roleTemplates: SelectedCompanyRole[];
  reason: string;
};
export function companyMembershipFormValues(
  grant: CompanyMemberGrant | null,
): CompanyMembershipFormValues {
  return {
    active: grant?.member.membershipActive ?? false,
    permissions: [...(grant?.directPermissions ?? [])],
    roleTemplates: grant?.roleTemplates.map((role) => ({ ...role, retainApplied: true })) ?? [],
    reason: "",
  };
}
