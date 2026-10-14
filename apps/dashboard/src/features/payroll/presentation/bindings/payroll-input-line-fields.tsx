import { memo } from "react";
import type { Control } from "react-hook-form";
import type { Failure } from "../../../../core/domain/result";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import {
  usePayrollInputDay,
  usePayrollInputDeduction,
  usePayrollInputEarning,
} from "../controllers/use-payroll-input-line";
import type { PayrollInputFormValues } from "../models/payroll-input-form-values";
import { PayrollInputDayFieldsPage } from "../pages/payroll-input-day-fields-page";
import { PayrollInputMoneyFieldsPage } from "../pages/payroll-input-money-fields-page";

type Props = {
  control: Control<PayrollInputFormValues>;
  index: number;
  editable: boolean;
  failure: Failure | null;
  locale: Locale;
  onRemove: (index: number) => void;
};
export const PayrollInputEarningFields = memo(function PayrollInputEarningFields(props: Props) {
  const form = usePayrollInputEarning(props.control, props.index);
  return <PayrollInputMoneyFieldsPage {...props} form={form} category="earnings" />;
});
export const PayrollInputDeductionFields = memo(function PayrollInputDeductionFields(props: Props) {
  const form = usePayrollInputDeduction(props.control, props.index);
  return <PayrollInputMoneyFieldsPage {...props} form={form} category="deductions" />;
});
export const PayrollInputDayFields = memo(function PayrollInputDayFields(props: Props) {
  const form = usePayrollInputDay(props.control, props.index);
  return <PayrollInputDayFieldsPage {...props} form={form} />;
});
