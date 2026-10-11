import type { HolidayDto } from "../data/models/holiday-dto";
import type { HolidayChange } from "../domain/entities/holiday-change";
import type { HolidayId, WorkHoliday } from "../domain/entities/work-holiday";
import { company, identifier } from "./workforce-fixture";

export { company, identifier, operation, signal } from "./workforce-fixture";
export const holidayAccess = {
  companyId: company,
  permissions: ["company.read", "workforce.manage"],
};
export const holidayRangeFixture = { from: "2026-01-01", until: "2026-12-31" };
export const holidayDtoFixture = (n = 1, version = 0): HolidayDto => ({
  id: identifier(n),
  version,
  workDate: `2026-01-${String(n).padStart(2, "0")}`,
  name: `Holiday ${n}`,
  active: true,
});
export const holidayFixture = (n = 1, version = 0): WorkHoliday => ({
  ...holidayDtoFixture(n, version),
  id: identifier(n) as HolidayId,
  companyId: company,
});
export const holidayChangeFixture = (): HolidayChange => ({
  id: identifier(),
  workDate: "2026-01-01",
  name: "New year",
  active: true,
  expectedVersion: null,
  reason: "Annual calendar",
});
