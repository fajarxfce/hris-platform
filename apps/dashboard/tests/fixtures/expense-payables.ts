import type {
  ExpensePayableDto,
  ExpensePayablePageDto,
} from "../../src/features/expenses/data/models/expense-payable-dto";
import { expenseIdentifier } from "./expense-claims";

export function expensePayable(index = 0, company = 0): ExpensePayableDto {
  return {
    claimId: expenseIdentifier(190_000 + index, company),
    submissionId: expenseIdentifier(195_000 + index, company),
    employmentId: expenseIdentifier(196_000 + index, company),
    employeeNumber: `PAY-${index + 1}`,
    employeeName: `Payable Employee ${index + 1}`,
    title: `Travel claim ${index + 1}`,
    amount: index === 0 ? "150000.75" : "275000.25",
    currency: "IDR",
    approvedAt: new Date(Date.UTC(2026, 9, 13, 5, 0) - index * 60_000).toISOString(),
    availableActions: ["PREPARE"],
  };
}
export function expensePayablePage(count = 2, offset = 0): ExpensePayablePageDto {
  const items = Array.from({ length: count }, (_, index) => expensePayable(index + offset));
  return { items, nextCursor: count === 20 ? (items.at(-1)?.claimId ?? null) : null };
}
