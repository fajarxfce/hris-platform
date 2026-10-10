import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { AccountId } from "../../../../core/domain/identifiers";
import type {
  CompanyMember,
  CompanyMemberGrant,
  CompanyMemberPage,
} from "../../domain/entities/company-member";
import type {
  CompanyMemberDto,
  CompanyMemberGrantDto,
  CompanyMemberPageDto,
} from "../models/company-member-dto";

export function toCompanyMember(value: CompanyMemberDto): CompanyMember {
  if (new Set(value.permissions).size !== value.permissions.length)
    throw new InvalidHttpResponseError();
  return Object.freeze({
    id: value.id.toLowerCase() as AccountId,
    email: value.email,
    displayName: value.displayName,
    accountActive: value.accountActive,
    membershipActive: value.active,
    version: value.version,
    permissions: Object.freeze([...value.permissions]),
  });
}

export function toCompanyMemberPage(
  value: CompanyMemberPageDto,
  after: string | null,
): CompanyMemberPage {
  const items = value.items.map(toCompanyMember);
  const cursor = value.nextCursor?.toLowerCase() ?? null;
  if (
    new Set(items.map((item) => item.id)).size !== items.length ||
    items.some(
      (item, index) => item.id <= (index === 0 ? (after ?? "") : (items[index - 1]?.id ?? "")),
    ) ||
    (cursor !== null && (cursor !== items.at(-1)?.id || cursor === after))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: cursor });
}

export function toCompanyMemberGrant(
  value: CompanyMemberGrantDto,
  accountId: AccountId,
): CompanyMemberGrant {
  const member = toCompanyMember(value.member);
  if (
    member.id !== accountId ||
    new Set(value.directPermissions).size !== value.directPermissions.length ||
    new Set(value.roleTemplates.map((role) => role.id.toLowerCase())).size !==
      value.roleTemplates.length
  )
    throw new InvalidHttpResponseError();
  const roles = value.roleTemplates.map((role) => {
    if (new Set(role.permissions).size !== role.permissions.length)
      throw new InvalidHttpResponseError();
    return Object.freeze({
      ...role,
      id: role.id.toLowerCase(),
      permissions: Object.freeze([...role.permissions]),
    });
  });
  return Object.freeze({
    member,
    directPermissions: Object.freeze([...value.directPermissions]),
    roleTemplates: Object.freeze(roles),
  });
}
