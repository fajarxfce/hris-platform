import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { LeaveLedger } from "../../domain/entities/leave-ledger";
import type { LeaveLedgerDto } from "../models/leave-ledger-dto";
import { toLeaveBalance } from "./leave-balance-mapper";

export function toLeaveLedger(
  raw: LeaveLedgerDto,
  companyId: CompanyId,
  employee: string,
  type: string,
  year: number,
  after: string | null,
): LeaveLedger {
  if (raw.employee.id.toLowerCase() !== employee || raw.typeId.toLowerCase() !== type)
    throw new InvalidHttpResponseError();
  let previous: { at: bigint; id: string } | null = null;
  const entries = raw.entries.items.map((row) => {
    const at = utcInstantMicroseconds(row.recordedAt);
    const id = row.id.toLowerCase();
    if (
      at === null ||
      id === after ||
      (previous !== null && (at > previous.at || (at === previous.at && id >= previous.id)))
    )
      throw new InvalidHttpResponseError();
    previous = { at, id };
    return Object.freeze({
      ...row,
      id,
      sourceId: row.sourceId.toLowerCase(),
      requestId: row.requestId?.toLowerCase() ?? null,
      actorId: row.actorId.toLowerCase(),
    });
  });
  const nextCursor = raw.entries.nextCursor?.toLowerCase() ?? null;
  if (
    new Set(entries.map((row) => row.id)).size !== entries.length ||
    (raw.balance.accountId === null && entries.length !== 0) ||
    (nextCursor !== null &&
      (entries.length !== 20 || nextCursor !== entries.at(-1)?.id || nextCursor === after))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    companyId,
    employee: Object.freeze({ ...raw.employee, id: employee }),
    typeId: type,
    typeCode: raw.typeCode,
    typeName: raw.typeName,
    balance: toLeaveBalance(raw.balance, year),
    entries: Object.freeze(entries),
    nextCursor,
  });
}
