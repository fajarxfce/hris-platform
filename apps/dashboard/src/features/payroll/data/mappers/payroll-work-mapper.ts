import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { PayrollWorkAdjustment } from "../../domain/entities/payroll-work-adjustment";
import type { PayrollWorkDay } from "../../domain/entities/payroll-work-day";
import type { PayrollWorkAdjustmentDto, PayrollWorkDaysDto } from "../models/payroll-work-dto";

export function toPayrollWorkAdjustment(
  raw: PayrollWorkAdjustmentDto,
  month: string,
): PayrollWorkAdjustment {
  if (
    !isCalendarDate(raw.workDate) ||
    raw.workDate.slice(0, 7) !== month ||
    !Number.isFinite(Date.parse(raw.recordedAt))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ ...raw, id: raw.id.toLowerCase() });
}
export function toPayrollWorkDays(
  raw: PayrollWorkDaysDto,
  month: string,
): readonly PayrollWorkDay[] {
  const [year, number] = month.split("-").map(Number);
  if (
    year === undefined ||
    number === undefined ||
    raw.length !== new Date(Date.UTC(year, number, 0)).getUTCDate()
  )
    throw new InvalidHttpResponseError();
  const requests = new Set<string>();
  return Object.freeze(
    raw.map((day, index) => {
      if (
        !isCalendarDate(day.date) ||
        day.date !== `${month}-${String(index + 1).padStart(2, "0")}`
      )
        throw new InvalidHttpResponseError();
      const overtime = Object.freeze(
        day.overtime.map((line) => {
          const requestId = line.requestId.toLowerCase();
          if (requests.has(requestId)) throw new InvalidHttpResponseError();
          requests.add(requestId);
          return Object.freeze({ ...line, requestId });
        }),
      );
      return Object.freeze({ ...day, overtime });
    }),
  );
}
