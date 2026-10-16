import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpCompanyMemberDataSource } from "../data/datasources/http-company-member-data-source";
import { HttpIdentityDataSource } from "../data/datasources/http-identity-data-source";
import { HttpManagedAccountDataSource } from "../data/datasources/http-managed-account-data-source";
import { HttpRoleTemplateDataSource } from "../data/datasources/http-role-template-data-source";
import { RemoteCompanyMemberRepository } from "../data/repositories/remote-company-member-repository";
import { RemoteIdentityRepository } from "../data/repositories/remote-identity-repository";
import { RemoteManagedAccountRepository } from "../data/repositories/remote-managed-account-repository";
import { RemoteRoleTemplateRepository } from "../data/repositories/remote-role-template-repository";
import { BeginMfaEnrollment } from "../domain/usecases/begin-mfa-enrollment";
import { ConfirmMfaEnrollment } from "../domain/usecases/confirm-mfa-enrollment";
import { CreateCompanyMembership } from "../domain/usecases/create-company-membership";
import { LoadAssignablePermissions } from "../domain/usecases/load-assignable-permissions";
import { LoadCompanyAccess } from "../domain/usecases/load-company-access";
import { LoadCompanyAccountCandidates } from "../domain/usecases/load-company-account-candidates";
import { LoadCompanyMember } from "../domain/usecases/load-company-member";
import { LoadCompanyMembers } from "../domain/usecases/load-company-members";
import { LoadIdentityProviders } from "../domain/usecases/load-identity-providers";
import { LoadManagedAccount } from "../domain/usecases/load-managed-account";
import { LoadManagedAccounts } from "../domain/usecases/load-managed-accounts";
import { LoadRoleTemplate } from "../domain/usecases/load-role-template";
import { LoadRoleTemplates } from "../domain/usecases/load-role-templates";
import { LoadSession } from "../domain/usecases/load-session";
import { PrepareCompanyMembership } from "../domain/usecases/prepare-company-membership";
import { SaveAccountAccess } from "../domain/usecases/save-account-access";
import { SaveCompanyMembership } from "../domain/usecases/save-company-membership";
import { SaveRoleTemplate } from "../domain/usecases/save-role-template";
import { SignIn } from "../domain/usecases/sign-in";
import { SignOut } from "../domain/usecases/sign-out";
import { VerifyMfa } from "../domain/usecases/verify-mfa";

export function createIdentityFeature(http: HttpClient) {
  const source = new HttpIdentityDataSource(http);
  const repository = new RemoteIdentityRepository(source);
  const members = new RemoteCompanyMemberRepository(new HttpCompanyMemberDataSource(http));
  const roles = new RemoteRoleTemplateRepository(new HttpRoleTemplateDataSource(http));
  const accounts = new RemoteManagedAccountRepository(new HttpManagedAccountDataSource(http));
  return {
    loadCompanyAccountCandidates: new LoadCompanyAccountCandidates(accounts),
    prepareCompanyMembership: new PrepareCompanyMembership(members, accounts, roles),
    createCompanyMembership: new CreateCompanyMembership(members),
    loadManagedAccounts: new LoadManagedAccounts(accounts),
    loadManagedAccount: new LoadManagedAccount(accounts),
    saveAccountAccess: new SaveAccountAccess(accounts),
    loadRoleTemplates: new LoadRoleTemplates(roles),
    loadRoleTemplate: new LoadRoleTemplate(roles),
    loadAssignablePermissions: new LoadAssignablePermissions(roles),
    saveRoleTemplate: new SaveRoleTemplate(roles),
    loadCompanyMembers: new LoadCompanyMembers(members),
    loadCompanyMember: new LoadCompanyMember(members),
    saveCompanyMembership: new SaveCompanyMembership(members),
    loadSession: new LoadSession(repository),
    loadCompanyAccess: new LoadCompanyAccess(repository),
    loadProviders: new LoadIdentityProviders(repository),
    signIn: new SignIn(repository),
    signOut: new SignOut(repository),
    beginEnrollment: new BeginMfaEnrollment(repository),
    confirmEnrollment: new ConfirmMfaEnrollment(repository),
    verifyMfa: new VerifyMfa(repository),
  };
}

export type IdentityFeature = ReturnType<typeof createIdentityFeature>;
