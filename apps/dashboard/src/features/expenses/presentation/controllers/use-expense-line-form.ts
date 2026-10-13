import { useLayoutEffect, useReducer } from "react";
import { useController, useForm } from "react-hook-form";
import type { ExpenseDraftReferences } from "../../domain/entities/expense-draft-references";
import type { ExpenseLine } from "../../domain/entities/expense-line";
import { mergeExpenseDraftLabels } from "../models/expense-draft-labels";
import type { ExpenseDraftOption } from "../models/expense-draft-option";
import type { ExpenseDraftEditorController } from "./expense-draft-editor-controller";

type State = Readonly<{
  panel: "fields" | ExpenseDraftOption["kind"];
  labels: ExpenseDraftReferences;
}>;
export function useExpenseLineForm(
  controller: ExpenseDraftEditorController,
  line: ExpenseLine,
  references: ExpenseDraftReferences,
) {
  const form = useForm<ExpenseLine>({ defaultValues: line });
  const occurredOn = useController({ name: "occurredOn", control: form.control }),
    description = useController({ name: "description", control: form.control }),
    amount = useController({ name: "amount", control: form.control }),
    category = useController({ name: "categoryId", control: form.control }),
    costCenter = useController({ name: "costCenterId", control: form.control }),
    receipts = useController({ name: "receiptRevisionIds", control: form.control });
  const [selection, update] = useReducer((_previous: State, next: State) => next, {
    panel: "fields",
    labels: mergeExpenseDraftLabels(references, references, [line]),
  });
  useLayoutEffect(() => {
    controller.setLineDirty(line.id, form.formState.isDirty);
  }, [controller, line.id, form.formState.isDirty]);
  return {
    occurredOn: occurredOn.field,
    description: description.field,
    amount: amount.field,
    categoryId: category.field.value,
    costCenterId: costCenter.field.value,
    receiptIds: receipts.field.value,
    labels: selection.labels,
    panel: selection.panel,
    openCategories: () => update({ ...selection, panel: "category" }),
    openCostCenters: () => update({ ...selection, panel: "costCenter" }),
    openReceipts: () => update({ ...selection, panel: "receipt" }),
    back: () => update({ ...selection, panel: "fields" }),
    select: (option: ExpenseDraftOption) => {
      if (option.value.companyId !== references.companyId || option.kind !== selection.panel)
        return;
      let labels = selection.labels;
      if (option.kind === "category") {
        form.setValue("categoryId", option.id, { shouldDirty: true });
        labels = {
          ...labels,
          categories: [
            ...labels.categories,
            { id: option.id, code: option.value.code, name: option.value.name, active: true },
          ],
        };
      } else if (option.kind === "costCenter") {
        form.setValue("costCenterId", option.id, { shouldDirty: true });
        labels = {
          ...labels,
          costCenters: [
            ...labels.costCenters,
            { id: option.id, code: option.value.code, name: option.value.name, active: true },
          ],
        };
      } else {
        if (option.value.employmentId !== references.employmentId) return;
        const selected = form.getValues("receiptRevisionIds");
        if (selected.length >= 3 || selected.includes(option.id)) return;
        form.setValue("receiptRevisionIds", [...selected, option.id], { shouldDirty: true });
        labels = {
          ...labels,
          receipts: [
            ...labels.receipts,
            {
              revisionId: option.id,
              documentId: option.value.documentId,
              fileName: option.value.fileName,
              mediaType: option.value.mediaType,
              size: option.value.size,
              status: "READY",
            },
          ],
        };
      }
      update({
        panel: "fields",
        labels: mergeExpenseDraftLabels(labels, labels, [form.getValues()]),
      });
    },
    clearCostCenter: () => {
      form.setValue("costCenterId", null, { shouldDirty: true });
      update({
        ...selection,
        labels: mergeExpenseDraftLabels(selection.labels, selection.labels, [form.getValues()]),
      });
    },
    removeReceipt: (id: string) => {
      form.setValue(
        "receiptRevisionIds",
        form.getValues("receiptRevisionIds").filter((value) => value !== id),
        { shouldDirty: true },
      );
      update({
        ...selection,
        labels: mergeExpenseDraftLabels(selection.labels, selection.labels, [form.getValues()]),
      });
    },
    submit: form.handleSubmit((values) => controller.saveLine(values, selection.labels)),
    cancel: controller.closeLine,
    remove: controller.removeLine,
  };
}
