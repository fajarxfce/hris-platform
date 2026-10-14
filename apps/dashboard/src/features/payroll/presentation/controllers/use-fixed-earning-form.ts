import { type Control, useController } from "react-hook-form";
import type { CompensationFormValues } from "../models/compensation-form-values";
export function useFixedEarningForm(control: Control<CompensationFormValues>, index: number) {
  const code = useController({ name: `fixedEarnings.${index}.code`, control }).field;
  const name = useController({ name: `fixedEarnings.${index}.name`, control }).field;
  const amount = useController({ name: `fixedEarnings.${index}.amount`, control }).field;
  const taxable = useController({ name: `fixedEarnings.${index}.taxable`, control }).field;
  return { code, name, amount, taxable };
}
