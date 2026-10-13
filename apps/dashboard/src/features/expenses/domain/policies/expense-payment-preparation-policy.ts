import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpensePayable } from "../entities/expense-payable";
import type { ExpensePaymentBatchId } from "../entities/expense-payment-batch";
import type { ExpensePaymentDestination } from "../entities/expense-payment-destination";
import type { ExpensePaymentPreparation } from "../entities/expense-payment-preparation";
import type { ExpensePaymentPreparationCommand } from "../entities/expense-payment-preparation-command";
import type { ExpensePaymentSelection } from "../entities/expense-payment-selection";
import { expenseAmountMinor } from "./expense-amount";
import { canReadExpensePayments } from "./expense-payment-read-policy";
import { expensePaymentActionWasRejected } from "./expense-payment-transition-policy";

const controlCharacters = /\p{Cc}/u;
export const canPrepareExpensePayable = (access: CompanyAccess, payable: ExpensePayable): boolean =>
  access.companyId === payable.companyId &&
  canReadExpensePayments(access.permissions) &&
  payable.availableActions.includes("PREPARE");
export function addExpensePaymentSelection(
  access: CompanyAccess,
  selected: readonly ExpensePaymentSelection[],
  payable: ExpensePayable,
): Result<readonly ExpensePaymentSelection[]> {
  if (!canPrepareExpensePayable(access, payable))
    return failed("expense_payment_preparation_unavailable");
  if (selected.length >= 100) return failed("expense_payment_selection_limit");
  if (
    selected.some(
      (item) =>
        item.payable.claimId.toLowerCase() === payable.claimId.toLowerCase() ||
        item.payable.submissionId.toLowerCase() === payable.submissionId.toLowerCase(),
    )
  )
    return failed("expense_payment_duplicate_claim");
  return success(Object.freeze([...selected, Object.freeze({ payable, destination: null })]));
}
export function prepareExpensePaymentDestination(
  input: ExpensePaymentDestination,
): Result<ExpensePaymentDestination> {
  const bankCode = input.bankCode.trim(),
    accountNumber = input.accountNumber.trim(),
    accountName = input.accountName.trim();
  if (
    !/^[A-Z0-9]{2,12}$/u.test(bankCode) ||
    !/^[0-9]{6,34}$/u.test(accountNumber) ||
    accountName.length < 1 ||
    accountName.length > 120 ||
    controlCharacters.test(accountName)
  )
    return failed("invalid_expense_payment_destination");
  return success(Object.freeze({ bankCode, accountNumber, accountName }));
}
/** Validate and freeze the instructions displayed for human review, before IDs are assigned. */
export function reviewExpensePaymentPreparation(
  access: CompanyAccess,
  selected: readonly ExpensePaymentSelection[],
  inputTitle: string,
  inputReason: string,
): Result<ExpensePaymentPreparation> {
  if (
    !canReadExpensePayments(access.permissions) ||
    selected.some((item) => item.payable.companyId !== access.companyId)
  )
    return failed("access_denied");
  if (selected.some((item) => !canPrepareExpensePayable(access, item.payable)))
    return failed("expense_payment_preparation_unavailable");
  const title = inputTitle.trim(),
    reason = inputReason.trim();
  if (
    title.length < 1 ||
    title.length > 160 ||
    controlCharacters.test(title) ||
    reason.length < 1 ||
    reason.length > 1000 ||
    selected.length < 1 ||
    selected.length > 100 ||
    new Set(selected.map((item) => item.payable.claimId.toLowerCase())).size !== selected.length ||
    new Set(selected.map((item) => item.payable.submissionId.toLowerCase())).size !==
      selected.length
  )
    return failed("invalid_expense_payment_batch");
  const items: Array<ExpensePaymentPreparation["items"][number]> = [];
  for (const item of selected) {
    if (
      !isUuid(item.payable.claimId) ||
      !isUuid(item.payable.submissionId) ||
      (expenseAmountMinor(item.payable.amount) ?? 0n) <= 0n ||
      item.payable.currency !== "IDR"
    )
      return failed("invalid_expense_payment_batch");
    if (!item.destination) return failed("invalid_expense_payment_destination");
    const destination = prepareExpensePaymentDestination(item.destination);
    if (!destination.ok) return destination;
    items.push(
      Object.freeze({
        payable: Object.freeze({
          ...item.payable,
          claimId: item.payable.claimId.toLowerCase(),
          submissionId: item.payable.submissionId.toLowerCase(),
          availableActions: Object.freeze([...item.payable.availableActions]),
        }),
        destination: destination.value,
      }),
    );
  }
  return success(
    Object.freeze({ companyId: access.companyId, title, reason, items: Object.freeze(items) }),
  );
}
export function prepareExpensePaymentCommand(
  access: CompanyAccess,
  operation: OperationId,
  id: string,
  review: ExpensePaymentPreparation,
  itemIds: readonly string[],
): Result<ExpensePaymentPreparationCommand> {
  if (review.companyId !== access.companyId) return failed("access_denied");
  if (
    !isUuid(operation) ||
    !isUuid(id) ||
    itemIds.length < 1 ||
    itemIds.length > 100 ||
    itemIds.length !== review.items.length ||
    itemIds.some((value) => !isUuid(value)) ||
    new Set(itemIds.map((value) => value.toLowerCase())).size !== itemIds.length
  )
    return failed("invalid_expense_payment_batch");
  const checked = reviewExpensePaymentPreparation(
    access,
    review.items,
    review.title,
    review.reason,
  );
  if (!checked.ok) return checked;
  const items: Array<ExpensePaymentPreparationCommand["items"][number]> = [];
  for (const [index, item] of checked.value.items.entries()) {
    const itemId = itemIds[index];
    if (itemId === undefined) return failed("invalid_expense_payment_batch");
    items.push(
      Object.freeze({
        id: itemId.toLowerCase(),
        submissionId: item.payable.submissionId.toLowerCase(),
        destination: item.destination,
      }),
    );
  }
  return success(
    Object.freeze({
      id: id.toLowerCase() as ExpensePaymentBatchId,
      title: checked.value.title,
      reason: checked.value.reason,
      items: Object.freeze(items),
    }),
  );
}
export const expensePaymentPreparationWasRejected = (failure: Failure): boolean =>
  expensePaymentActionWasRejected(failure) ||
  [
    "invalid_expense_payment_batch",
    "invalid_expense_payment_destination",
    "expense_payment_preparation_unavailable",
    "expense_payment_requires_approved_claims",
    "expense_payment_batch_exists",
    "expense_payment_capacity",
    "expense_claim_payment_reserved",
    "expense_payment_attempt_limit",
  ].includes(failure.code);
