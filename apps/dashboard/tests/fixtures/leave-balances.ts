import type { EmployeeLeaveBalancesDto } from "../../src/features/leave/data/models/leave-balance-dto";
import type { LeaveLedgerDto } from "../../src/features/leave/data/models/leave-ledger-dto";
import { leaveEmployeeId, leaveId } from "./leave";

export const balanceTypeId = (index = 1) =>
  `b3000000-0000-4000-8000-${String(index).padStart(12, "0")}`;
export const balanceEntryId = (index = 1) =>
  `bd000000-0000-4000-8000-${String(index).padStart(12, "0")}`;
export function balanceDirectory(year = 2026, count = 23): EmployeeLeaveBalancesDto {
  return {
    employee: { id: leaveEmployeeId(), number: "EMP-0-1", name: "North Employee 01" },
    year,
    balances: {
      items: Array.from({ length: year === 2027 ? 0 : count }, (_, index) => ({
        typeId: balanceTypeId(index + 1),
        typeCode: index === 0 ? "ANNUAL" : `TYPE${String(index + 1).padStart(3, "0")}`,
        typeName: index === 0 ? "Annual leave" : `Leave type ${index + 1}`,
        balance: {
          accountId: `bc00${year}-0000-4000-8000-${String(index + 1).padStart(12, "0")}`,
          year,
          availableDays: year === 2025 ? "0" : "8.5",
          reservedDays: year === 2025 ? "0" : "1",
          consumedDays: "2.5",
          closed: year === 2025,
          version: year === 2025 ? 15 : 14,
        },
      })),
      nextCursor: null,
    },
  };
}
export function balanceDirectoryPage(
  year = 2026,
  after: string | null = null,
): EmployeeLeaveBalancesDto {
  const record = balanceDirectory(year);
  const available = record.balances.items.filter((item) => after === null || item.typeCode > after);
  return {
    ...record,
    balances: {
      items: available.slice(0, 20),
      nextCursor: available.length > 20 ? (available[19]?.typeCode ?? null) : null,
    },
  };
}
export function balanceLedger(year = 2026, type = balanceTypeId()): LeaveLedgerDto {
  const directory = balanceDirectory(year);
  const summary = directory.balances.items.find((item) => item.typeId === type) ?? {
    typeId: type,
    typeCode: "TYPE001",
    typeName: "Annual leave",
    balance: {
      accountId: null,
      year,
      availableDays: "0",
      reservedDays: "0",
      consumedDays: "0",
      closed: false,
      version: 0,
    },
  };
  return {
    ...summary,
    employee: directory.employee,
    entries: {
      items: Array.from({ length: year === 2027 ? 0 : 23 }, (_, index) => ({
        id: balanceEntryId(index + 1),
        kind: index === 0 ? "CONSUME" : "ADJUSTMENT",
        sourceId: index === 0 ? leaveId() : balanceEntryId(index + 1),
        requestId: index === 0 ? leaveId() : null,
        availableDeltaDays: index === 0 ? "0" : "0.5",
        reservedDeltaDays: index === 0 ? "-0.5" : "0",
        consumedDeltaDays: index === 0 ? "0.5" : "0",
        actorId: "20000000-0000-4000-8000-000000000001",
        recordedAt: new Date(Date.UTC(year, 8, 30, 12) - index * 60_000).toISOString(),
        reason: index === 0 ? "Final leave approval" : `Balance correction ${index + 1}`,
      })),
      nextCursor: null,
    },
  };
}
export function balanceLedgerPage(
  year = 2026,
  after: string | null = null,
  type = balanceTypeId(),
): LeaveLedgerDto {
  const record = balanceLedger(year, type);
  const start =
    after === null ? 0 : record.entries.items.findIndex((entry) => entry.id === after) + 1;
  const available = record.entries.items.slice(start);
  return {
    ...record,
    entries: {
      items: available.slice(0, 20),
      nextCursor: available.length > 20 ? (available[19]?.id ?? null) : null,
    },
  };
}
