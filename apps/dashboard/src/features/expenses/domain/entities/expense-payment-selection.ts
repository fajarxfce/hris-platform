import type { ExpensePayable } from "./expense-payable";
import type { ExpensePaymentDestination } from "./expense-payment-destination";
export type ExpensePaymentSelection = Readonly<{
  payable: ExpensePayable;
  destination: ExpensePaymentDestination | null;
}>;
