import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type {
  ApprovalDelegation,
  ApprovalDelegationId,
  ApprovalDelegationPage,
} from "../../domain/entities/approval-delegation";
import { validApprovalDelegationPeriod } from "../../domain/policies/approval-delegation-policy";
import type {
  ApprovalDelegationDto,
  ApprovalDelegationPageDto,
} from "../models/approval-delegation-dto";

export function toApprovalDelegation(
  dto: ApprovalDelegationDto,
  companyId: CompanyId,
): ApprovalDelegation {
  if (
    !validApprovalDelegationPeriod(dto.validFrom, dto.validUntil) ||
    dto.fromAccount.toLowerCase() === dto.toAccount.toLowerCase()
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...dto,
    companyId,
    id: dto.id.toLowerCase() as ApprovalDelegationId,
    fromAccount: dto.fromAccount.toLowerCase() as AccountId,
    toAccount: dto.toAccount.toLowerCase() as AccountId,
  });
}
export function toApprovalDelegationPage(
  dto: ApprovalDelegationPageDto,
  company: CompanyId,
  account: AccountId,
  after: string | null,
): ApprovalDelegationPage {
  const items = dto.items.map((item) => toApprovalDelegation(item, company));
  const nextCursor = dto.nextCursor?.toLowerCase() ?? null;
  if (
    items.some(
      (item, index) =>
        (item.fromAccount !== account && item.toAccount !== account) ||
        (after !== null && item.id <= after) ||
        (index > 0 && item.id <= (items[index - 1]?.id ?? "")),
    ) ||
    (nextCursor !== null && (items.length !== 20 || nextCursor !== items.at(-1)?.id))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor });
}
