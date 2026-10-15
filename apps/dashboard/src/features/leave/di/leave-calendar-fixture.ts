import type { CompanyId } from "../../../core/domain/identifiers";
import type { CompanyAccess } from "../../identity/domain/entities/session";
import type { LeaveCalendarDto, LeaveCalendarEntriesDto } from "../data/models/leave-calendar-dto";

export const calendarCompany = "10000000-0000-4000-8000-000000000001" as CompanyId;
export const calendarAccess: CompanyAccess = {
  companyId: calendarCompany,
  permissions: ["leave.read", "leave.team.read"],
};
export const calendarRequestId = (index: number): string =>
  `80000000-0000-4000-8000-${String(index).padStart(12, "0")}`;
export const calendarWire: LeaveCalendarDto = {
  month: "2026-10",
  scope: "COMPANY",
  companyDate: "2026-10-15",
  timezone: "Asia/Jakarta",
  days: [
    {
      date: "2026-10-05",
      employeeCount: 1,
      requestCount: 2,
      pendingCount: 1,
      approvedCount: 1,
      cancellationPendingCount: 0,
    },
    {
      date: "2026-10-15",
      employeeCount: 1,
      requestCount: 1,
      pendingCount: 0,
      approvedCount: 0,
      cancellationPendingCount: 1,
    },
  ],
};
export const calendarEntriesWire: LeaveCalendarEntriesDto = {
  date: "2026-10-15",
  scope: "COMPANY",
  companyDate: "2026-10-15",
  timezone: "Asia/Jakarta",
  entries: {
    items: [
      {
        requestId: calendarRequestId(1),
        employeeId: "90000000-0000-4000-8000-000000000001",
        employeeNumber: "EMP-001",
        employeeName: "Alex Employee",
        typeCode: "ANNUAL",
        typeName: "Annual leave",
        portion: "FIRST_HALF",
        status: "CANCELLATION_PENDING",
        version: 2,
      },
    ],
    nextCursor: null,
  },
};
export function calendarDeferred<T>() {
  let resolve!: (value: T) => void, reject!: (error: unknown) => void;
  const promise = new Promise<T>((complete, fail) => {
    resolve = complete;
    reject = fail;
  });
  return { promise, resolve, reject };
}
