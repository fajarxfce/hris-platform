import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { IncomeTaxRule } from "../../domain/entities/income-tax-rule";
import type { PayrollPolicy } from "../../domain/entities/payroll-policy";
import type { PayrollPolicyReview } from "../../domain/entities/payroll-policy-review";
import {
  isPayrollMonth,
  payrollAmountMinor,
} from "../../domain/policies/payroll-policy-validation";
import type {
  PayrollIncomeTaxRulesDto,
  PayrollPolicyDto,
  PayrollPolicyReviewDto,
} from "../models/payroll-policy-dto";

export function toPayrollPolicy(raw: PayrollPolicyDto, companyId: CompanyId): PayrollPolicy {
  const minimum = payrollAmountMinor(raw.minimumMonthlyWage),
    health = payrollAmountMinor(raw.healthWageCap),
    pension = payrollAmountMinor(raw.pensionWageCap);
  if (
    raw.appliedRevision > raw.version ||
    !isPayrollMonth(raw.effectiveFrom) ||
    !isPayrollMonth(raw.effectiveUntil) ||
    raw.effectiveUntil < raw.effectiveFrom ||
    raw.effectiveFrom.slice(0, 4) !== raw.effectiveUntil.slice(0, 4) ||
    minimum === null ||
    minimum <= 0n ||
    health === null ||
    health < minimum ||
    pension === null ||
    pension <= 0n
  )
    throw new InvalidHttpResponseError();
  const { currency: _, ...policy } = raw;
  return Object.freeze({
    ...policy,
    companyId,
    reviewReferences: Object.freeze([...raw.reviewReferences]),
  });
}

export function toPayrollPolicyReview(
  raw: PayrollPolicyReviewDto,
  company: CompanyId,
  month: string,
  after: string | null,
): PayrollPolicyReview {
  if (raw.asOf !== month) throw new InvalidHttpResponseError();
  const current = raw.current ? toPayrollPolicy(raw.current, company) : null;
  const effective = raw.effective ? toPayrollPolicy(raw.effective, company) : null;
  if (
    current &&
    (current.version !== current.appliedRevision ||
      (after !== null && Number(after) > current.version))
  )
    throw new InvalidHttpResponseError();
  if (
    !current &&
    (effective !== null ||
      after !== null ||
      raw.history.items.length ||
      raw.history.nextCursor !== null)
  )
    throw new InvalidHttpResponseError();
  if (
    effective &&
    (effective.version !== current?.version ||
      month < effective.effectiveFrom ||
      month > effective.effectiveUntil ||
      (effective.appliedRevision === current?.appliedRevision &&
        JSON.stringify(effective) !== JSON.stringify(current)))
  )
    throw new InvalidHttpResponseError();
  let previous = after === null ? -1 : Number(after);
  const items = raw.history.items.map((row) => {
    const policy = toPayrollPolicy(row.policy, company);
    if (
      policy.version !== current?.version ||
      policy.appliedRevision !== previous + 1 ||
      !Number.isFinite(Date.parse(row.recordedAt))
    )
      throw new InvalidHttpResponseError();
    if (
      (policy.appliedRevision === current?.appliedRevision &&
        JSON.stringify(policy) !== JSON.stringify(current)) ||
      (policy.appliedRevision === effective?.appliedRevision &&
        JSON.stringify(policy) !== JSON.stringify(effective))
    )
      throw new InvalidHttpResponseError();
    previous = policy.appliedRevision;
    return Object.freeze({
      policy,
      actorId: row.actorId.toLowerCase(),
      reason: row.reason,
      recordedAt: row.recordedAt,
    });
  });
  const cursor = raw.history.nextCursor;
  if (
    (cursor === null && previous !== (current?.version ?? -1)) ||
    (cursor !== null &&
      (items.length !== 20 || cursor !== String(previous) || previous >= (current?.version ?? -1)))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    asOf: month,
    current,
    effective,
    history: Object.freeze({ items: Object.freeze(items), nextCursor: cursor }),
  });
}

export function toIncomeTaxRules(raw: PayrollIncomeTaxRulesDto): readonly IncomeTaxRule[] {
  if (new Set(raw.map((rule) => rule.id)).size !== raw.length) throw new InvalidHttpResponseError();
  return Object.freeze(
    raw.map((rule) => {
      if (
        !isCalendarDate(rule.effectiveFrom) ||
        (rule.effectiveUntil !== null &&
          (!isCalendarDate(rule.effectiveUntil) || rule.effectiveUntil < rule.effectiveFrom))
      )
        throw new InvalidHttpResponseError();
      return Object.freeze({ ...rule, sources: Object.freeze([...rule.sources]) });
    }),
  );
}
