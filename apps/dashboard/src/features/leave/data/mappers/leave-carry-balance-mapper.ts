import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { LeaveCarryBalance } from "../../domain/entities/leave-carry-balance";
import type { LeaveBalanceDto } from "../models/leave-balance-dto";
import type { LeaveCarryBalanceDto } from "../models/leave-carry-balance-dto";

export function toLeaveCarryBalance(
  raw: LeaveCarryBalanceDto,
  balance: LeaveBalanceDto,
): LeaveCarryBalance {
  const awarded = Number(raw.awardedDays) * 2;
  const accounted = [
    raw.availableDays,
    raw.reservedDays,
    raw.consumedDays,
    raw.expiredDays,
    raw.transferredDays,
    raw.deductedDays,
  ].reduce((sum, value) => sum + Number(value) * 2, 0);
  if (
    balance.accountId === null ||
    awarded <= 0 ||
    awarded !== accounted ||
    Number(raw.availableDays) > Number(balance.availableDays) ||
    Number(raw.reservedDays) > Number(balance.reservedDays) ||
    Number(raw.consumedDays) > Number(balance.consumedDays) ||
    (balance.closed && (raw.availableDays !== "0" || raw.reservedDays !== "0")) ||
    (raw.validThrough === null) !== (raw.expiresAt === null)
  )
    throw new InvalidHttpResponseError();
  if (raw.validThrough !== null) {
    const lastDay = new Date(Date.UTC(balance.year, Number(raw.validThrough.slice(5, 7)), 0))
      .toISOString()
      .slice(0, 10);
    if (raw.validThrough !== lastDay) throw new InvalidHttpResponseError();
  }
  // Retain the original UTC deadline; timezone database updates must not rederive it.
  return Object.freeze({ ...raw, closingId: raw.closingId.toLowerCase() });
}
