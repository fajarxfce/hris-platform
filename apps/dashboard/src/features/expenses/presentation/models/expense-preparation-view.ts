import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpensePayablePage } from "../../domain/entities/expense-payable";
import { expenseAmountMinor } from "../../domain/policies/expense-amount";
import { canPrepareExpensePayable } from "../../domain/policies/expense-payment-preparation-policy";
import { expensePaymentMessages } from "../i18n/expense-payment-messages";
import { expensePreparationMessages } from "../i18n/expense-preparation-messages";
import { expenseAmountView } from "./expense-amount-view";
import type { ExpensePreparationState } from "./expense-preparation-state";

export function expensePreparationView(state: ExpensePreparationState, locale: Locale) {
  const text = expensePreparationMessages(locale),
    common = expensePaymentMessages(locale),
    number = new Intl.NumberFormat(locale);
  const items = state.review?.items ?? state.items;
  const total = items.reduce(
    (sum, item) => sum + (expenseAmountMinor(item.payable.amount) ?? 0n),
    0n,
  );
  const complete = items.filter((item) => item.destination !== null).length;
  const selectedId = state.panel.kind === "destination" ? state.panel.claimId : null;
  const selected = state.items.find((item) => item.payable.claimId === selectedId);
  return {
    canReview: items.length > 0 && complete === items.length,
    properties: [
      { label: text.selectedCount, value: number.format(items.length) },
      {
        label: text.total,
        value: expenseAmountView(
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
      id: payable.claimId,
      actionLabel: `${payable.title} · ${payable.employeeName}`,
      cells: [
        payable.title,
        `${payable.employeeName} · ${payable.employeeNumber}`,
        destination
          ? `${destination.bankCode} · •••• ${destination.accountNumber.slice(-4)}`
          : text.missingDestination,
        expenseAmountView(payable.amount, locale),
      ],
    })),
    reviewRows:
      state.review?.items.map(({ payable, destination }) => ({
        id: payable.claimId,
        cells: [
          payable.title,
          `${payable.employeeName} · ${payable.employeeNumber}`,
          destination.bankCode,
          destination.accountName,
          destination.accountNumber,
          expenseAmountView(payable.amount, locale),
        ],
      })) ?? [],
    selected: selected
      ? {
          id: selected.payable.claimId,
          original: selected.destination,
          properties: [
            { label: text.claim, value: selected.payable.title },
            {
              label: common.employee,
              value: `${selected.payable.employeeName} · ${selected.payable.employeeNumber}`,
            },
            { label: common.amount, value: expenseAmountView(selected.payable.amount, locale) },
          ],
        }
      : null,
  };
}

export function expensePayableRows(
  page: ExpensePayablePage,
  selectedIds: ReadonlySet<string>,
  access: CompanyAccess,
  locale: Locale,
  timezone: string,
) {
  const text = expensePreparationMessages(locale),
    date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: timezone });
  return page.items.map((payable) => {
    const selected = selectedIds.has(payable.claimId),
      available = canPrepareExpensePayable(access, payable);
    return {
      id: payable.claimId,
      actionLabel: `${payable.title} · ${payable.employeeName}`,
      actionDisabled: selected || !available || selectedIds.size >= 100,
      cells: [
        payable.title,
        `${payable.employeeName} · ${payable.employeeNumber}`,
        expenseAmountView(payable.amount, locale),
        date.format(new Date(payable.approvedAt)),
        selected ? text.selected : available ? text.available : text.unavailable,
      ],
    };
  });
}
