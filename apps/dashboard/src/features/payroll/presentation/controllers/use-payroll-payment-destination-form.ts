import { useLayoutEffect } from "react";
import { useController, useForm } from "react-hook-form";
import type { PayrollPaymentDestination } from "../../domain/entities/payroll-payment-destination";
import type { PayrollPaymentPreparationController } from "./payroll-payment-preparation-controller";
export function usePayrollPaymentDestinationForm(
  controller: PayrollPaymentPreparationController,
  assessmentId: string,
  original: PayrollPaymentDestination | null,
) {
  const form = useForm({
    defaultValues: original ?? { bankCode: "", accountNumber: "", accountName: "" },
  });
  const bankCode = useController({
    name: "bankCode",
    control: form.control,
    rules: { required: true, maxLength: 12 },
  });
  const accountNumber = useController({
    name: "accountNumber",
    control: form.control,
    rules: { required: true, maxLength: 34 },
  });
  const accountName = useController({
    name: "accountName",
    control: form.control,
    rules: { required: true, maxLength: 120 },
  });
  useLayoutEffect(() => {
    controller.setDestinationDirty(assessmentId, form.formState.isDirty);
  }, [controller, assessmentId, form.formState.isDirty]);
  return {
    bankCode: bankCode.field,
    accountNumber: accountNumber.field,
    accountName: accountName.field,
    invalid: Object.keys(form.formState.errors).length > 0,
    submit: form.handleSubmit(controller.saveDestination),
    cancel: controller.closePanel,
    remove: controller.removeSelected,
  };
}
