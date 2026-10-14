import { type Control, useController } from "react-hook-form";
import type { PayrollInputFormValues } from "../models/payroll-input-form-values";
export function usePayrollInputEarning(control: Control<PayrollInputFormValues>, index: number) {
  const code = useController({ control, name: `earnings.${index}.code` }).field,
    name = useController({ control, name: `earnings.${index}.name` }).field,
    amount = useController({ control, name: `earnings.${index}.amount` }).field,
    taxable = useController({ control, name: `earnings.${index}.taxable` }).field;
  return { code, name, amount, taxable };
}
export function usePayrollInputDeduction(control: Control<PayrollInputFormValues>, index: number) {
  const code = useController({ control, name: `deductions.${index}.code` }).field,
    name = useController({ control, name: `deductions.${index}.name` }).field,
    amount = useController({ control, name: `deductions.${index}.amount` }).field;
  return { code, name, amount };
}
export function usePayrollInputDay(control: Control<PayrollInputFormValues>, index: number) {
  const workDate = useController({ control, name: `dayResolutions.${index}.workDate` }).field,
    portion = useController({ control, name: `dayResolutions.${index}.portion` }).field,
    disposition = useController({ control, name: `dayResolutions.${index}.disposition` }).field,
    reference = useController({ control, name: `dayResolutions.${index}.reference` }).field;
  return { workDate, portion, disposition, reference };
}
