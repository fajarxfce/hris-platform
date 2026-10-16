import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { OidcBinding, OidcBindingId } from "../../domain/entities/oidc-binding";
import type { OidcBindingPage } from "../../domain/entities/oidc-binding-page";
import type { OidcBindingDto, OidcBindingPageDto } from "../models/oidc-binding-dto";

export function toOidcBinding(value: OidcBindingDto, accountId: AccountId): OidcBinding {
  return Object.freeze({ ...value, id: value.id.toLowerCase() as OidcBindingId, accountId });
}
export function toOidcBindingPage(
  value: OidcBindingPageDto,
  accountId: AccountId,
  after: string | null,
): OidcBindingPage {
  const items = value.items.map((item) => toOidcBinding(item, accountId));
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
