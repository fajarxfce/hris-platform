import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpCompanyMemberDataSource } from "../data/datasources/http-company-member-data-source";
import { HttpIdentityDataSource } from "../data/datasources/http-identity-data-source";
import { RemoteCompanyMemberRepository } from "../data/repositories/remote-company-member-repository";
import { RemoteIdentityRepository } from "../data/repositories/remote-identity-repository";
import { BeginMfaEnrollment } from "../domain/usecases/begin-mfa-enrollment";
import { ConfirmMfaEnrollment } from "../domain/usecases/confirm-mfa-enrollment";
import { LoadCompanyAccess } from "../domain/usecases/load-company-access";
import { LoadCompanyMember } from "../domain/usecases/load-company-member";
import { LoadCompanyMembers } from "../domain/usecases/load-company-members";
import { LoadIdentityProviders } from "../domain/usecases/load-identity-providers";
import { LoadSession } from "../domain/usecases/load-session";
import { SignIn } from "../domain/usecases/sign-in";
import { SignOut } from "../domain/usecases/sign-out";
import { VerifyMfa } from "../domain/usecases/verify-mfa";

export function createIdentityFeature(http: HttpClient) {
  const source = new HttpIdentityDataSource(http);
  const repository = new RemoteIdentityRepository(source);
  const members = new RemoteCompanyMemberRepository(new HttpCompanyMemberDataSource(http));
  return {
    loadCompanyMembers: new LoadCompanyMembers(members),
    loadCompanyMember: new LoadCompanyMember(members),
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
