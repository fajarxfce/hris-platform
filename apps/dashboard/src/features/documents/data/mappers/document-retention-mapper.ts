import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type {
  DocumentRetentionPolicy,
  DocumentRetentionPolicyChange,
  DocumentRetentionPolicyPage,
} from "../../domain/entities/document-retention-policy";
import type {
  DocumentRetentionPolicyDto,
  DocumentRetentionPolicyPageDto,
  DocumentRetentionReceiptDto,
} from "../models/document-retention-dto";

export function toDocumentRetentionPolicy(
  raw: DocumentRetentionPolicyDto,
  company: CompanyId,
): DocumentRetentionPolicy {
  if (!raw.reason.trim()) throw new InvalidHttpResponseError();
  return Object.freeze({
    ...raw,
    companyId: company,
    id: raw.id.toLowerCase(),
    actorId: raw.actorId.toLowerCase(),
  });
}
export function toDocumentRetentionPolicies(
  raw: DocumentRetentionPolicyDto[],
  company: CompanyId,
): readonly DocumentRetentionPolicy[] {
  const items = raw.map((policy) => toDocumentRetentionPolicy(policy, company));
  if (
    new Set(items.map((policy) => policy.id)).size !== items.length ||
    new Set(items.map((policy) => policy.classification)).size !== items.length
  )
    throw new InvalidHttpResponseError();
  return Object.freeze(items);
}
export function toDocumentRetentionPolicyHistory(
  raw: DocumentRetentionPolicyPageDto,
  company: CompanyId,
  policy: DocumentRetentionPolicy,
  after: string | null,
): DocumentRetentionPolicyPage {
  const items = raw.items.map((item) => toDocumentRetentionPolicy(item, company));
  let previous = after === null ? -1 : Number(after);
  for (const item of items) {
    if (
      item.id !== policy.id ||
      item.classification !== policy.classification ||
      item.version <= previous
    )
      throw new InvalidHttpResponseError();
    previous = item.version;
  }
  if (
    raw.nextCursor !== null &&
    (items.length !== 20 ||
      Number(raw.nextCursor) !== items.at(-1)?.version ||
      raw.nextCursor === after)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: raw.nextCursor });
}
export function toDocumentRetentionReceipt(
  raw: DocumentRetentionReceiptDto,
  input: DocumentRetentionPolicyChange,
) {
  if (raw.id.toLowerCase() !== input.policyId || raw.version !== (input.expectedVersion ?? -1) + 1)
    throw new InvalidHttpResponseError();
  return Object.freeze({ id: raw.id.toLowerCase(), version: raw.version });
}
