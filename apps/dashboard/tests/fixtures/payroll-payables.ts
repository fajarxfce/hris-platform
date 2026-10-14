import type {
  PayrollPayableDto,
  PayrollPayablePageDto,
} from "../../src/features/payroll/data/models/payroll-payable-dto";
import { payrollIdentifier } from "./payroll-payment-identifiers";

export function payrollPayable(index = 0, company = 0): PayrollPayableDto {
  return {
    assessmentId: payrollIdentifier(190_000 + index, company),
    employmentId: payrollIdentifier(196_000 + index, company),
    employeeNumber: `PAY-${index + 1}`,
    employeeName: `Payable Employee ${index + 1}`,
    taxMonth: "2026-09",
    plannedPaymentDate: "2026-10-13",
    amount: index === 0 ? "150000.75" : "275000.25",
    currency: "IDR",
    publishedAt: new Date(Date.UTC(2026, 9, 13, 5, 0) - index * 60_000).toISOString(),
  };
}
export function payrollPayablePage(count = 2, offset = 0): PayrollPayablePageDto {
  const items = Array.from({ length: count }, (_, index) => payrollPayable(index + offset));
  return { items, nextCursor: count === 20 ? (items.at(-1)?.assessmentId ?? null) : null };
}
