import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { PayrollPeriod, PayrollPeriods } from "../../domain/entities/payroll-period";
import type { PayrollPeriodDetails } from "../../domain/entities/payroll-period-details";
import type { PayrollPeriodEmployees } from "../../domain/entities/payroll-period-employee";
import type { PayrollPeriodSearch } from "../../domain/entities/payroll-period-search";
import { validatePayrollPeriodFields } from "../../domain/policies/payroll-period-policy";
import type {
  PayrollPeriodDetailsDto,
  PayrollPeriodDto,
  PayrollPeriodEmployeesDto,
  PayrollPeriodsDto,
} from "../models/payroll-period-dto";
export function toPayrollPeriod(raw: PayrollPeriodDto, company: CompanyId): PayrollPeriod {
  if (
    validatePayrollPeriodFields({ ...raw, reason: "Stored period" }) ||
    raw.plannedPaymentMonth !== raw.plannedPaymentDate.slice(0, 7) ||
    (["DRAFT", "CANCELLED"].includes(raw.status)
      ? raw.currentRunId !== null
      : raw.currentRunId === null)
  )
    throw new InvalidHttpResponseError();
  try {
    new Intl.DateTimeFormat("en", { timeZone: raw.timezone });
  } catch {
    throw new InvalidHttpResponseError();
  }
  return Object.freeze({
    ...raw,
    companyId: company,
    id: raw.id.toLowerCase(),
    authorId: raw.authorId.toLowerCase(),
    currentRunId: raw.currentRunId?.toLowerCase() ?? null,
  });
}
export function toPayrollPeriods(
  raw: PayrollPeriodsDto,
  company: CompanyId,
  search: PayrollPeriodSearch,
): PayrollPeriods {
  const items = raw.items.map((row) => toPayrollPeriod(row, company)),
    cursor = raw.nextCursor?.toLowerCase() ?? null;
  if (
    items.some(
      (row, index) =>
        row.earningsMonth < search.from ||
        row.earningsMonth > search.until ||
        (search.status !== null && row.status !== search.status) ||
        row.id <= (items[index - 1]?.id ?? search.after ?? ""),
    ) ||
    (cursor !== null && (items.length !== 20 || cursor !== items.at(-1)?.id))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: cursor });
}
export function toPayrollPeriodEmployees(
  raw: PayrollPeriodEmployeesDto,
  after: string | null,
): PayrollPeriodEmployees {
  const items = raw.items.map((row) => Object.freeze({ ...row, id: row.id.toLowerCase() }));
  if (
    new Set(items.map((row) => row.id)).size !== items.length ||
    new Set(items.map((row) => row.employeeNumber)).size !== items.length ||
    items.some((row) => row.employeeNumber === after) ||
    (raw.nextCursor !== null &&
      (items.length !== 50 ||
        raw.nextCursor !== items.at(-1)?.employeeNumber ||
        raw.nextCursor === after))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: raw.nextCursor });
}
export function toPayrollPeriodDetails(
  raw: PayrollPeriodDetailsDto,
  company: CompanyId,
  id: string,
  memberAfter: string | null,
  historyAfter: string | null,
): PayrollPeriodDetails {
  const period = toPayrollPeriod(raw.period, company),
    members = raw.members.items.map((row) =>
      Object.freeze({ ...row, employeeId: row.employeeId.toLowerCase() }),
    ),
    history = raw.history.items.map((row) =>
      Object.freeze({
        ...row,
        actorId: row.actorId.toLowerCase(),
        runId: row.runId?.toLowerCase() ?? null,
      }),
    ),
    nextMember = raw.members.nextCursor?.toLowerCase() ?? null;
  if (
    period.id !== id ||
    members.length > period.participantCount ||
    members.some(
      (row, index) =>
        row.employeeId <= (members[index - 1]?.employeeId ?? memberAfter ?? "") ||
        (row.inputVersion === null) !== (row.inputStatus === null),
    ) ||
    (nextMember !== null && (members.length !== 50 || nextMember !== members.at(-1)?.employeeId)) ||
    (memberAfter === null && nextMember === null && members.length !== period.participantCount)
  )
    throw new InvalidHttpResponseError();
  if (
    (historyAfter !== null && Number(historyAfter) > period.version) ||
    history.some(
      (row) =>
        row.version === period.version &&
        (row.status !== period.status || row.runId !== period.currentRunId),
    ) ||
    history.some(
      (row, index) =>
        row.version !==
          (history[index - 1]?.version ?? (historyAfter === null ? -1 : Number(historyAfter))) +
            1 || row.version > period.version,
    ) ||
    (historyAfter === null && history[0]?.version !== 0) ||
    (raw.history.nextCursor !== null &&
      (history.length !== 20 ||
        raw.history.nextCursor !== String(history.at(-1)?.version) ||
        Number(raw.history.nextCursor) >= period.version)) ||
    (raw.history.nextCursor === null &&
      history.at(-1)?.version !== period.version &&
      Number(historyAfter ?? -1) < period.version)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    period,
    members: Object.freeze({ items: Object.freeze(members), nextCursor: nextMember }),
    history: Object.freeze({ items: Object.freeze(history), nextCursor: raw.history.nextCursor }),
  });
}
