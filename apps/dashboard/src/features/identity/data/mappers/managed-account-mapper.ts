import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { ManagedAccount } from "../../domain/entities/managed-account";
import type { ManagedAccountPage } from "../../domain/entities/managed-account-page";
import type { ManagedAccountReview } from "../../domain/entities/managed-account-review";
import type {
  ManagedAccountDto,
  ManagedAccountPageDto,
  ManagedAccountReviewDto,
} from "../models/managed-account-dto";
export function toManagedAccount(value: ManagedAccountDto): ManagedAccount {
  if (
    new Set(value.platformPermissions).size !== value.platformPermissions.length ||
    (value.active && value.invitationPending)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    id: value.id.toLowerCase() as AccountId,
    email: value.email,
    displayName: value.displayName,
    active: value.active,
    invitationPending: value.invitationPending,
    mfaConfigured: value.mfaConfigured,
    version: value.version,
    platformPermissions: Object.freeze([...value.platformPermissions].sort()),
  });
}
export function toManagedAccountPage(
  value: ManagedAccountPageDto,
  after: string | null,
): ManagedAccountPage {
  const items = value.items.map(toManagedAccount);
  const cursor = value.nextCursor?.toLowerCase() ?? null;
  if (
    items.some(
      (item, index) => item.id <= (index === 0 ? (after ?? "") : (items[index - 1]?.id ?? "")),
    ) ||
    (cursor !== null && (items.length !== 50 || cursor !== items.at(-1)?.id || cursor === after))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: cursor });
}
export function toManagedAccountReview(
  value: ManagedAccountReviewDto,
  id: AccountId,
): ManagedAccountReview {
  const account = toManagedAccount(value.account);
  if (
    account.id !== id ||
    new Set(value.assignablePermissions).size !== value.assignablePermissions.length
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    account,
    assignablePermissions: Object.freeze([...value.assignablePermissions].sort()),
  });
}
