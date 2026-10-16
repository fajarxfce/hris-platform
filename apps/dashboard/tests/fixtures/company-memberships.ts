import type { CompanyMemberGrantDto } from "../../src/features/identity/data/models/company-member-dto";
import { roleTemplateId } from "./role-templates";

export const companyMembershipId = "21000000-0000-4000-8000-000000000001";
export function companyMembershipGrant(): CompanyMemberGrantDto {
  return {
    member: {
      id: companyMembershipId,
      email: "colleague@example.invalid",
      displayName: "Example Colleague",
      accountActive: true,
      active: true,
      permissions: ["company.read", "people.team.read"],
      version: 4,
    },
    directPermissions: ["company.read"],
    roleTemplates: [
      {
        id: roleTemplateId(),
        code: "REVIEWER_1",
        name: "Team reviewer",
        permissions: ["people.team.read"],
        version: 0,
      },
    ],
  };
}
