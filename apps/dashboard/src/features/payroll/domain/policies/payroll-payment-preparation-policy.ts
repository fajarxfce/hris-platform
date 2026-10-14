import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPayable } from "../entities/payroll-payable";
import type { PayrollPaymentBatchId } from "../entities/payroll-payment-batch";
import type { PayrollPaymentDestination } from "../entities/payroll-payment-destination";
import type { PayrollPaymentPreparation } from "../entities/payroll-payment-preparation";
import type { PayrollPaymentPreparationCommand } from "../entities/payroll-payment-preparation-command";
import type { PayrollPaymentSelection } from "../entities/payroll-payment-selection";
import { payrollPaymentAmountMinor } from "./payroll-payment-amount";
import { canReadPayrollPayments } from "./payroll-payment-read-policy";
import { payrollPaymentActionWasRejected } from "./payroll-payment-transition-policy";

const controlCharacters = /\p{Cc}/u;
export const canPreparePayrollPayable = (access: CompanyAccess, payable: PayrollPayable): boolean =>
  access.companyId === payable.companyId && canReadPayrollPayments(access.permissions);
export function addPayrollPaymentSelection(
  access: CompanyAccess,
  selected: readonly PayrollPaymentSelection[],
  payable: PayrollPayable,
): Result<readonly PayrollPaymentSelection[]> {
  if (!canPreparePayrollPayable(access, payable))
    return failed("payroll_payment_preparation_unavailable");
  if (selected.length >= 100) return failed("payroll_payment_selection_limit");
  if (
    selected.some(
      (item) => item.payable.assessmentId.toLowerCase() === payable.assessmentId.toLowerCase(),
    )
  )
    return failed("payroll_payment_duplicate_assessment");
  return success(Object.freeze([...selected, Object.freeze({ payable, destination: null })]));
}
export function preparePayrollPaymentDestination(
  input: PayrollPaymentDestination,
): Result<PayrollPaymentDestination> {
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
    return failed("invalid_payroll_payment_destination");
  return success(Object.freeze({ bankCode, accountNumber, accountName }));
}
/** Validate and freeze the instructions displayed for human review, before IDs are assigned. */
export function reviewPayrollPaymentPreparation(
  access: CompanyAccess,
  selected: readonly PayrollPaymentSelection[],
  inputTitle: string,
  inputReason: string,
): Result<PayrollPaymentPreparation> {
  if (
    !canReadPayrollPayments(access.permissions) ||
    selected.some((item) => item.payable.companyId !== access.companyId)
  )
    return failed("access_denied");
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
    new Set(selected.map((item) => item.payable.assessmentId.toLowerCase())).size !==
      selected.length
  )
    return failed("invalid_payroll_payment_batch");
  const items: Array<PayrollPaymentPreparation["items"][number]> = [];
  for (const item of selected) {
    if (
      !isUuid(item.payable.assessmentId) ||
      (payrollPaymentAmountMinor(item.payable.amount) ?? 0n) <= 0n ||
      item.payable.currency !== "IDR"
    )
      return failed("invalid_payroll_payment_batch");
    if (!item.destination) return failed("invalid_payroll_payment_destination");
    const destination = preparePayrollPaymentDestination(item.destination);
    if (!destination.ok) return destination;
    items.push(
      Object.freeze({
        payable: Object.freeze({
          ...item.payable,
          assessmentId: item.payable.assessmentId.toLowerCase(),
        }),
        destination: destination.value,
      }),
    );
  }
  return success(
    Object.freeze({ companyId: access.companyId, title, reason, items: Object.freeze(items) }),
  );
}
export function preparePayrollPaymentCommand(
  access: CompanyAccess,
  operation: OperationId,
  id: string,
  review: PayrollPaymentPreparation,
  itemIds: readonly string[],
): Result<PayrollPaymentPreparationCommand> {
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
    return failed("invalid_payroll_payment_batch");
  const checked = reviewPayrollPaymentPreparation(
    access,
    review.items,
    review.title,
    review.reason,
  );
  if (!checked.ok) return checked;
  const items: Array<PayrollPaymentPreparationCommand["items"][number]> = [];
  for (const [index, item] of checked.value.items.entries()) {
    const itemId = itemIds[index];
    if (itemId === undefined) return failed("invalid_payroll_payment_batch");
    items.push(
      Object.freeze({
        id: itemId.toLowerCase(),
        assessmentId: item.payable.assessmentId.toLowerCase(),
        destination: item.destination,
      }),
    );
  }
  return success(
    Object.freeze({
      id: id.toLowerCase() as PayrollPaymentBatchId,
      title: checked.value.title,
      reason: checked.value.reason,
      items: Object.freeze(items),
    }),
  );
}
export const payrollPaymentPreparationWasRejected = (failure: Failure): boolean =>
  payrollPaymentActionWasRejected(failure) ||
  [
    "invalid_payroll_payment_batch",
    "invalid_payroll_payment_destination",
    "payroll_payment_preparation_unavailable",
    "payroll_assessment_not_payable",
    "payroll_payment_batch_exists",
    "payroll_payment_capacity",
    "payroll_assessment_payment_reserved",
    "payroll_payment_attempt_limit",
  ].includes(failure.code);
