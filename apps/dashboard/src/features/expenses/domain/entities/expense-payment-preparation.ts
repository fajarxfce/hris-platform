import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ExpensePayable } from "./expense-payable";
import type { ExpensePaymentDestination } from "./expense-payment-destination";
export type ExpensePaymentPreparation = Readonly<{
  companyId: CompanyId;
  title: string;
  reason: string;
  items: readonly Readonly<{ payable: ExpensePayable; destination: ExpensePaymentDestination }>[];
}>;
