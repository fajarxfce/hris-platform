import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { PayrollInput, PayrollInputSummary } from "../../domain/entities/payroll-input";
import type { PayrollInputChange } from "../../domain/entities/payroll-input-change";
import type {
  PayrollInputEmployees,
  PayrollInputReview,
  PayrollInputSearch,
} from "../../domain/entities/payroll-input-review";
import {
  normalizePayrollInputTerms,
  validatePayrollInputTerms,
} from "../../domain/policies/payroll-input-validation";
import type {
  PayrollInputChangeDto,
  PayrollInputDto,
  PayrollInputEmployeesDto,
  PayrollInputReviewDto,
  PayrollInputSummaryDto,
} from "../models/payroll-input-dto";
export function toPayrollInputSummary(
  raw: PayrollInputSummaryDto,
  employee: string,
  month: string,
): PayrollInputSummary {
  if (
    raw.employeeId.toLowerCase() !== employee ||
    raw.earningsMonth !== month ||
    (raw.status === "VERIFIED") !== (raw.verifiedBy !== null) ||
    raw.verifiedBy?.toLowerCase() === raw.preparedBy.toLowerCase() ||
    !Number.isFinite(Date.parse(raw.recordedAt))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...raw,
    id: raw.id.toLowerCase(),
    employeeId: employee,
    preparedBy: raw.preparedBy.toLowerCase(),
    verifiedBy: raw.verifiedBy?.toLowerCase() ?? null,
  });
}
export function toPayrollInput(
  raw: PayrollInputDto,
  company: CompanyId,
  employee: string,
  month: string,
): PayrollInput {
  const { workJobId, workPeriodVersion, workAdjustmentId, employmentVersion, terms, ...summary } =
    raw;
  if (validatePayrollInputTerms(month, terms)) throw new InvalidHttpResponseError();
  return Object.freeze({
    ...toPayrollInputSummary(summary, employee, month),
    companyId: company,
    workJobId: workJobId.toLowerCase(),
    workPeriodVersion,
    workAdjustmentId: workAdjustmentId?.toLowerCase() ?? null,
    employmentVersion,
    terms: normalizePayrollInputTerms(terms),
  });
}
export function toPayrollInputEmployees(
  raw: PayrollInputEmployeesDto,
  search: PayrollInputSearch,
): PayrollInputEmployees {
  const items = raw.items.map((item) => {
    if (
      (item.inputVersion === null) !== (item.inputStatus === null) ||
      (search.status !== null && (item.inputStatus ?? "UNPREPARED") !== search.status)
    )
      throw new InvalidHttpResponseError();
    return Object.freeze({ ...item, id: item.id.toLowerCase() });
  });
  if (
    new Set(items.map((item) => item.id)).size !== items.length ||
    new Set(items.map((item) => item.employeeNumber)).size !== items.length ||
    items.some((item) => item.employeeNumber === search.after) ||
    (raw.nextCursor !== null &&
      (items.length !== 50 ||
        raw.nextCursor !== items.at(-1)?.employeeNumber ||
        raw.nextCursor === search.after))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: raw.nextCursor });
}
export function toPayrollInputReview(
  raw: PayrollInputReviewDto,
  company: CompanyId,
  employee: string,
  month: string,
  after: string | null,
): PayrollInputReview {
  if (
    raw.earningsMonth !== month ||
    raw.employee.id.toLowerCase() !== employee ||
    (raw.source !== null && raw.source.earningsMonth !== month) ||
    new Set(raw.availableActions).size !== raw.availableActions.length
  )
    throw new InvalidHttpResponseError();
  const current = raw.current ? toPayrollInput(raw.current, company, employee, month) : null;
  const source = raw.source
    ? Object.freeze({
        ...raw.source,
        periodId: raw.source.periodId.toLowerCase(),
        jobId: raw.source.jobId?.toLowerCase() ?? null,
        adjustment: raw.source.adjustment
          ? Object.freeze({
              ...raw.source.adjustment,
              id: raw.source.adjustment.id.toLowerCase(),
            })
          : null,
      })
    : null;
  if (
    source?.adjustment &&
    (!source.closed ||
      !source.includesEmployee ||
      !source.jobId ||
      !isCalendarDate(source.adjustment.workDate) ||
      source.adjustment.workDate.slice(0, 7) !== month ||
      !Number.isFinite(Date.parse(source.adjustment.recordedAt)))
  )
    throw new InvalidHttpResponseError();
  if (
    (after !== null && (!current || Number(after) > current.version)) ||
    (!current && (raw.history.items.length || raw.history.nextCursor !== null))
  )
    throw new InvalidHttpResponseError();
  let previous = after === null ? -1 : Number(after);
  const items = raw.history.items.map((row) => {
    const value = toPayrollInputSummary(row, employee, month);
    if (
      value.version !== previous + 1 ||
      value.version > (current?.version ?? -1) ||
      value.id !== current?.id
    )
      throw new InvalidHttpResponseError();
    if (
      value.version === current?.version &&
      (value.status !== current.status ||
        value.preparedBy !== current.preparedBy ||
        value.verifiedBy !== current.verifiedBy ||
        value.reason !== current.reason ||
        value.recordedAt !== current.recordedAt)
    )
      throw new InvalidHttpResponseError();
    previous = value.version;
    return value;
  });
  const next = raw.history.nextCursor;
  if (
    (next === null && previous !== (current?.version ?? -1)) ||
    (next !== null &&
      (items.length !== 20 || next !== String(previous) || previous >= (current?.version ?? -1)))
  )
    throw new InvalidHttpResponseError();
  if (
    raw.availableActions.length &&
    (raw.frozen ||
      !source?.closed ||
      !source.includesEmployee ||
      !source.jobId ||
      current?.version === 999)
  )
    throw new InvalidHttpResponseError();
  if (
    raw.availableActions.includes("VERIFY") &&
    (current?.status !== "DRAFT" ||
      current.workJobId !== source?.jobId ||
      current.workPeriodVersion !== source?.version ||
      current.workAdjustmentId !== (source?.adjustment?.id ?? null) ||
      current.employmentVersion !== raw.employee.employmentVersion)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    earningsMonth: month,
    employee: Object.freeze({ ...raw.employee, id: employee }),
    source,
    current,
    history: Object.freeze({ items: Object.freeze(items), nextCursor: next }),
    frozen: raw.frozen,
    availableActions: Object.freeze([...raw.availableActions]),
  });
}
export function toPayrollInputChangeDto(input: PayrollInputChange): PayrollInputChangeDto {
  return {
    workJobId: input.workJobId,
    ...(input.workAdjustmentId === null ? {} : { workAdjustmentId: input.workAdjustmentId }),
    expectedWorkPeriodVersion: input.expectedWorkPeriodVersion,
    expectedEmploymentVersion: input.expectedEmploymentVersion,
    expectedVersion: input.expectedVersion,
    reason: input.reason,
    terms: {
      earnings: input.terms.earnings.map((line) => ({ ...line })),
      deductions: input.terms.deductions.map((line) => ({ ...line })),
      nonCashTaxable: input.terms.nonCashTaxable,
      scheduledMonthUnits: input.terms.scheduledMonthUnits,
      dayResolutions: input.terms.dayResolutions.map((day) => ({ ...day })),
      holidayAllowance: input.terms.holidayAllowance ? { ...input.terms.holidayAllowance } : null,
      reviewReference: input.terms.reviewReference,
    },
  };
}
