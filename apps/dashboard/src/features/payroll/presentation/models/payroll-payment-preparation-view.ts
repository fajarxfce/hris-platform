import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPayablePage } from "../../domain/entities/payroll-payable";
import { payrollPaymentAmountMinor } from "../../domain/policies/payroll-payment-amount";
import { canPreparePayrollPayable } from "../../domain/policies/payroll-payment-preparation-policy";
import { payrollPaymentMessages } from "../i18n/payroll-payment-messages";
import { payrollPaymentPreparationMessages } from "../i18n/payroll-payment-preparation-messages";
import type { PayrollPaymentPreparationState } from "./payroll-payment-preparation-state";
import { payrollAmountView } from "./payroll-policy-view";

export function payrollPaymentPreparationView(
  state: PayrollPaymentPreparationState,
  locale: Locale,
) {
  const text = payrollPaymentPreparationMessages(locale),
    common = payrollPaymentMessages(locale),
    number = new Intl.NumberFormat(locale);
  const items = state.review?.items ?? state.items;
  const total = items.reduce(
    (sum, item) => sum + (payrollPaymentAmountMinor(item.payable.amount) ?? 0n),
    0n,
  );
  const complete = items.filter((item) => item.destination !== null).length;
  const selectedId = state.panel.kind === "destination" ? state.panel.assessmentId : null;
  const selected = state.items.find((item) => item.payable.assessmentId === selectedId);
  return {
    canReview: items.length > 0 && complete === items.length,
    properties: [
      { label: text.selectedCount, value: number.format(items.length) },
      {
        label: text.total,
        value: payrollAmountView(
          `${total / 100n}.${String(total % 100n).padStart(2, "0")}`,
          locale,
        ),
      },
      ...(state.review
        ? []
        : [
            { label: text.readyCount, value: number.format(complete) },
            { label: text.pendingCount, value: number.format(items.length - complete) },
          ]),
    ],
    rows: state.items.map(({ payable, destination }) => ({
      id: payable.assessmentId,
      actionLabel: `${payable.taxMonth} · ${payable.employeeName}`,
      cells: [
        payable.taxMonth,
        `${payable.employeeName} · ${payable.employeeNumber}`,
        String(payable.assessmentRevision),
        destination
          ? `${destination.bankCode} · •••• ${destination.accountNumber.slice(-4)}`
          : text.missingDestination,
        payrollAmountView(payable.amount, locale),
      ],
    })),
    reviewRows:
      state.review?.items.map(({ payable, destination }) => ({
        id: payable.assessmentId,
        cells: [
          payable.taxMonth,
          `${payable.employeeName} · ${payable.employeeNumber}`,
          String(payable.assessmentRevision),
          destination.bankCode,
          destination.accountName,
          destination.accountNumber,
          payrollAmountView(payable.amount, locale),
        ],
      })) ?? [],
    selected: selected
      ? {
          id: selected.payable.assessmentId,
          original: selected.destination,
          properties: [
            { label: text.taxMonth, value: selected.payable.taxMonth },
            {
              label: common.employee,
              value: `${selected.payable.employeeName} · ${selected.payable.employeeNumber}`,
            },
            { label: common.revision, value: String(selected.payable.assessmentRevision) },
            { label: common.amount, value: payrollAmountView(selected.payable.amount, locale) },
          ],
        }
      : null,
  };
}

export function payrollPayableRows(
  page: PayrollPayablePage,
  selectedIds: ReadonlySet<string>,
  access: CompanyAccess,
  locale: Locale,
  timezone: string,
) {
  const text = payrollPaymentPreparationMessages(locale),
    date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: timezone });
  return page.items.map((payable) => {
    const selected = selectedIds.has(payable.assessmentId),
      available = canPreparePayrollPayable(access, payable);
    return {
      id: payable.assessmentId,
      actionLabel: `${payable.taxMonth} · ${payable.employeeName}`,
      actionDisabled: selected || !available || selectedIds.size >= 100,
      cells: [
        payable.taxMonth,
        `${payable.employeeName} · ${payable.employeeNumber}`,
        String(payable.assessmentRevision),
        payrollAmountView(payable.amount, locale),
        date.format(new Date(payable.publishedAt)),
        selected ? text.selected : available ? text.available : text.unavailable,
      ],
    };
  });
}
