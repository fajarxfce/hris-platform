import type { LoadAssignablePermissions } from "../../domain/usecases/load-assignable-permissions";
import type { LoadCompanyMember } from "../../domain/usecases/load-company-member";
import type { LoadCompanyMembers } from "../../domain/usecases/load-company-members";
import type { LoadRoleTemplate } from "../../domain/usecases/load-role-template";
import type { LoadRoleTemplates } from "../../domain/usecases/load-role-templates";
import type { SaveRoleTemplate } from "../../domain/usecases/save-role-template";

export type IdentityAdministrationUseCases = Readonly<{
  loadRoleTemplates: Pick<LoadRoleTemplates, "execute">;
  loadRoleTemplate: Pick<LoadRoleTemplate, "execute">;
  loadAssignablePermissions: Pick<LoadAssignablePermissions, "execute">;
  saveRoleTemplate: Pick<SaveRoleTemplate, "execute">;
  loadCompanyMembers: Pick<LoadCompanyMembers, "execute">;
  loadCompanyMember: Pick<LoadCompanyMember, "execute">;
}>;
