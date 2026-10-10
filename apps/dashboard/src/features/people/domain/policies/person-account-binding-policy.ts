import type { AccountId } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import type { CompanyMember } from "../../../identity/domain/entities/company-member";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PersonProfile } from "../entities/person-profile";
import { canManagePersonProfile } from "./person-profile-policy";

export function canLinkPersonAccounts(permissions: readonly string[]): boolean {
  return ["people.account.link", "people.profile.manage", "identity.manage"].every((permission) =>
    permissions.includes(permission),
  );
}

export function canLinkPersonAccount(access: CompanyAccess, profile: PersonProfile): boolean {
  return (
    canManagePersonProfile(access, profile) &&
    canLinkPersonAccounts(access.permissions) &&
    profile.accountId === null
  );
}

export function isAccountBindingCandidate(member: CompanyMember, authorId: AccountId): boolean {
  return member.accountActive && member.membershipActive && member.id !== authorId;
}

/** A later rejection cannot resolve an earlier attempt whose response was lost. */
export function accountBindingWasRejected(failure: Failure): boolean {
  return [
    "invalid_account_binding",
    "person_account_link_access_required",
    "independent_account_binding_required",
    "account_membership_required",
    "person_account_already_bound",
    "person_profile_not_found",
    "profile_owner_required",
    "stale_version",
    "data_conflict",
    "access_denied",
    "company_access_denied",
    "company_required",
    "authentication_required",
    "session_revoked",
    "unauthenticated",
    "mfa_required",
    "mfa_setup_required",
    "recent_authentication_required",
    "csrf_invalid",
    "company_module_disabled",
    "company_maintenance",
    "client_update_required",
    "client_version_required",
    "invalid_client_version",
    "request_rate_limited",
  ].includes(failure.code);
}
