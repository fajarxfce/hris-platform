import { useLayoutEffect } from "react";
import { useController, useForm } from "react-hook-form";
import type { ExpensePaymentDestination } from "../../domain/entities/expense-payment-destination";
import type { ExpensePreparationController } from "./expense-preparation-controller";
export function useExpenseDestinationForm(
  controller: ExpensePreparationController,
  claimId: string,
  original: ExpensePaymentDestination | null,
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
    controller.setDestinationDirty(claimId, form.formState.isDirty);
  }, [controller, claimId, form.formState.isDirty]);
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
