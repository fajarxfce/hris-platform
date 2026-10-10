import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyMember, CompanyMemberGrant } from "../../domain/entities/company-member";
import { companyMemberMessages } from "../i18n/company-member-messages";

export function companyMemberRows(items: readonly CompanyMember[], locale: Locale) {
  const text = companyMemberMessages(locale);
  return items.map((member) => ({
    id: member.id,
    actionLabel: member.displayName,
    cells: [
      member.displayName,
      member.email,
      member.accountActive ? text.active : text.inactive,
      member.membershipActive ? text.active : text.inactive,
    ],
  }));
}

export function companyMemberProperties(grant: CompanyMemberGrant, locale: Locale) {
  const text = companyMemberMessages(locale);
  const member = grant.member;
  return [
    { label: text.name, value: member.displayName },
    { label: text.email, value: member.email },
    { label: text.identifier, value: member.id },
    { label: text.account, value: member.accountActive ? text.active : text.inactive },
    { label: text.membership, value: member.membershipActive ? text.active : text.inactive },
    { label: text.version, value: String(member.version) },
    { label: text.effective, value: member.permissions.join(", ") || text.none },
    { label: text.direct, value: grant.directPermissions.join(", ") || text.none },
  ];
}
