import type { CompanyId } from "../../../../core/domain/identifiers";
import type { PayrollPayable } from "./payroll-payable";
import type { PayrollPaymentDestination } from "./payroll-payment-destination";
export type PayrollPaymentPreparation = Readonly<{
  companyId: CompanyId;
  title: string;
  reason: string;
  items: readonly Readonly<{ payable: PayrollPayable; destination: PayrollPaymentDestination }>[];
}>;
