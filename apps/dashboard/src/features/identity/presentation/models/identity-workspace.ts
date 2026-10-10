import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { CompanyAccess, CompanyMembership, Session } from "../../domain/entities/session";

/** Last authorized UI scope. A pending identity check may retain it only while hidden. */
export type IdentityWorkspace = Readonly<{
  revision: number;
  accountId: AccountId;
  company: CompanyMembership | null;
  companies: readonly CompanyMembership[];
  platformPermissions: readonly string[];
  access: CompanyAccess | null;
}>;

export function sameWorkspaceMemberships(
  workspace: IdentityWorkspace,
  session: Session,
  companyId: CompanyId | null,
): boolean {
  if (
    workspace.accountId !== session.account.id ||
    (workspace.company?.id ?? null) !== companyId ||
    workspace.companies.length !== session.companies.length ||
    !samePermissions(workspace.platformPermissions, session.permissions)
  )
    return false;
  const companies = new Map(session.companies.map((company) => [company.id, company]));
  return workspace.companies.every((previous) => {
    const current = companies.get(previous.id);
    return (
      current?.code === previous.code &&
      current.name === previous.name &&
      current.timezone === previous.timezone
    );
  });
}

export function sameWorkspaceAccess(
  previous: CompanyAccess | null,
  current: CompanyAccess | null,
): boolean {
  if (previous === null || current === null) return previous === current;
  return (
    previous.companyId === current.companyId &&
    samePermissions(previous.permissions, current.permissions)
  );
}

function samePermissions(previous: readonly string[], current: readonly string[]): boolean {
  const permissions = new Set(current);
  return (
    new Set(previous).size === permissions.size &&
    previous.every((permission) => permissions.has(permission))
  );
}
