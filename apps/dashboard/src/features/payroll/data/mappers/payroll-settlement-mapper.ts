import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { PayrollSettlement } from "../../domain/entities/payroll-settlement";
import type { PayrollSettlementDto } from "../models/payroll-settlement-dto";
import { payrollPaymentTotalMinor } from "./payroll-payment-amount";

export function toPayrollSettlement(raw: PayrollSettlementDto): PayrollSettlement {
  const entitlement = payrollPaymentTotalMinor(raw.entitlement),
    paid = payrollPaymentTotalMinor(raw.confirmedPaid),
    recovered = payrollPaymentTotalMinor(raw.confirmedRecovered),
    balance = entitlement - paid + recovered;
  if (
    recovered > paid ||
    payrollPaymentTotalMinor(raw.payable) !== (balance > 0n ? balance : 0n) ||
    payrollPaymentTotalMinor(raw.recoverable) !== (balance < 0n ? -balance : 0n)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ ...raw });
}
