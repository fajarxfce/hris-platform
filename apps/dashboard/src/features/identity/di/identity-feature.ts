import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpAccountCredentialDataSource } from "../data/datasources/http-account-credential-data-source";
import { HttpAccountInvitationDataSource } from "../data/datasources/http-account-invitation-data-source";
import { HttpCompanyMemberDataSource } from "../data/datasources/http-company-member-data-source";
import { HttpIdentityDataSource } from "../data/datasources/http-identity-data-source";
import { HttpManagedAccountDataSource } from "../data/datasources/http-managed-account-data-source";
import { HttpOidcBindingDataSource } from "../data/datasources/http-oidc-binding-data-source";
import { HttpRoleTemplateDataSource } from "../data/datasources/http-role-template-data-source";
import { RemoteAccountCredentialRepository } from "../data/repositories/remote-account-credential-repository";
import { RemoteAccountInvitationRepository } from "../data/repositories/remote-account-invitation-repository";
import { RemoteCompanyMemberRepository } from "../data/repositories/remote-company-member-repository";
import { RemoteIdentityRepository } from "../data/repositories/remote-identity-repository";
import { RemoteManagedAccountRepository } from "../data/repositories/remote-managed-account-repository";
import { RemoteOidcBindingRepository } from "../data/repositories/remote-oidc-binding-repository";
import { RemoteRoleTemplateRepository } from "../data/repositories/remote-role-template-repository";
import { BeginMfaEnrollment } from "../domain/usecases/begin-mfa-enrollment";
import { ConfirmAccountCredential } from "../domain/usecases/confirm-account-credential";
import { ConfirmMfaEnrollment } from "../domain/usecases/confirm-mfa-enrollment";
import { CreateCompanyMembership } from "../domain/usecases/create-company-membership";
import { InviteAccount } from "../domain/usecases/invite-account";
import { LoadAssignablePermissions } from "../domain/usecases/load-assignable-permissions";
import { LoadCompanyAccess } from "../domain/usecases/load-company-access";
import { LoadCompanyAccountCandidates } from "../domain/usecases/load-company-account-candidates";
import { LoadCompanyMember } from "../domain/usecases/load-company-member";
import { LoadCompanyMembers } from "../domain/usecases/load-company-members";
import { LoadIdentityProviders } from "../domain/usecases/load-identity-providers";
import { LoadManagedAccount } from "../domain/usecases/load-managed-account";
import { LoadManagedAccounts } from "../domain/usecases/load-managed-accounts";
import { LoadOidcBindings } from "../domain/usecases/load-oidc-bindings";
import { LoadRoleTemplate } from "../domain/usecases/load-role-template";
import { LoadRoleTemplates } from "../domain/usecases/load-role-templates";
import { LoadSession } from "../domain/usecases/load-session";
import { PrepareCompanyMembership } from "../domain/usecases/prepare-company-membership";
import { PrepareOidcBinding } from "../domain/usecases/prepare-oidc-binding";
import { RequestPasswordRecovery } from "../domain/usecases/request-password-recovery";
import { SaveAccountAccess } from "../domain/usecases/save-account-access";
import { SaveCompanyMembership } from "../domain/usecases/save-company-membership";
import { SaveOidcBinding } from "../domain/usecases/save-oidc-binding";
import { SaveRoleTemplate } from "../domain/usecases/save-role-template";
import { SignIn } from "../domain/usecases/sign-in";
import { SignOut } from "../domain/usecases/sign-out";
import { VerifyMfa } from "../domain/usecases/verify-mfa";

export function createIdentityFeature(http: HttpClient) {
  const source = new HttpIdentityDataSource(http);
  const repository = new RemoteIdentityRepository(source);
  const invitations = new RemoteAccountInvitationRepository(
    new HttpAccountInvitationDataSource(http),
  );
  const credentials = new RemoteAccountCredentialRepository(
    new HttpAccountCredentialDataSource(http),
  );
  const members = new RemoteCompanyMemberRepository(new HttpCompanyMemberDataSource(http));
  const roles = new RemoteRoleTemplateRepository(new HttpRoleTemplateDataSource(http));
  const accounts = new RemoteManagedAccountRepository(new HttpManagedAccountDataSource(http));
  const oidc = new RemoteOidcBindingRepository(new HttpOidcBindingDataSource(http));
  return {
    loadOidcBindings: new LoadOidcBindings(accounts, oidc),
    prepareOidcBinding: new PrepareOidcBinding(accounts, oidc),
    saveOidcBinding: new SaveOidcBinding(oidc),
    inviteAccount: new InviteAccount(invitations),
    confirmAccountCredential: new ConfirmAccountCredential(credentials),
    requestPasswordRecovery: new RequestPasswordRecovery(credentials),
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
