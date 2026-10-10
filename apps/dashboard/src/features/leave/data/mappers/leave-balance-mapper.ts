import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { EmployeeLeaveBalances } from "../../domain/entities/employee-leave-balances";
import type { LeaveBalance } from "../../domain/entities/leave-balance";
import type { EmployeeLeaveBalancesDto, LeaveBalanceDto } from "../models/leave-balance-dto";

export function toLeaveBalance(raw: LeaveBalanceDto, year: number): LeaveBalance {
  if (
    raw.year !== year ||
    (raw.accountId === null &&
      (raw.version !== 0 ||
        raw.closed ||
        [raw.availableDays, raw.reservedDays, raw.consumedDays].some((value) => value !== "0")))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ ...raw, accountId: raw.accountId?.toLowerCase() ?? null });
}

export function toEmployeeLeaveBalances(
  raw: EmployeeLeaveBalancesDto,
  companyId: CompanyId,
  employee: string,
  year: number,
  after: string | null,
): EmployeeLeaveBalances {
  if (raw.employee.id.toLowerCase() !== employee || raw.year !== year)
    throw new InvalidHttpResponseError();
  const items = raw.balances.items.map((row) =>
    Object.freeze({
      typeId: row.typeId.toLowerCase(),
      typeCode: row.typeCode,
      typeName: row.typeName,
      balance: toLeaveBalance(row.balance, year),
    }),
  );
  const nextCursor = raw.balances.nextCursor;
  if (
    new Set(items.map((row) => row.typeId)).size !== items.length ||
    new Set(items.map((row) => row.typeCode)).size !== items.length ||
    new Set(items.map((row) => row.balance.accountId)).size !== items.length ||
    items.some((row) => row.typeCode === after || row.balance.accountId === null) ||
    (nextCursor !== null &&
      (items.length !== 20 || nextCursor !== items.at(-1)?.typeCode || nextCursor === after))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    companyId,
    employee: Object.freeze({ ...raw.employee, id: employee }),
    year,
    items: Object.freeze(items),
    nextCursor,
  });
}
