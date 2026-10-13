import { Field } from "@fluentui/react-components";
import { Add20Regular, ArrowLeft20Regular, Delete20Regular } from "@fluentui/react-icons";
import type { ReactNode } from "react";
import type { Failure } from "../../../../core/domain/result";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useExpenseLineForm } from "../controllers/use-expense-line-form";
import { expenseDraftFieldError, expenseDraftMessages } from "../i18n/expense-draft-messages";
import type { expenseLineSelectionView } from "../models/expense-draft-view";
export function ExpenseLinePage(props: {
  form: ReturnType<typeof useExpenseLineForm>;
  view: ReturnType<typeof expenseLineSelectionView>;
  failure: Failure | null;
  creating: boolean;
  locale: Locale;
  picker: ReactNode;
}) {
  const { form, locale, view } = props,
    text = expenseDraftMessages(locale);
  return (
    <AppDetailsPanel
      open
      title={text.item}
      closeLabel={messages(locale).close}
      onClose={form.cancel}
      width={600}
    >
      {form.panel !== "fields" ? (
        <>
          <AppButton appearance="subtle" icon={<ArrowLeft20Regular />} onClick={form.back}>
            {text.backToItem}
          </AppButton>
          {props.picker}
        </>
      ) : (
        <form className="app-editor-form" onSubmit={form.submit} noValidate aria-label={text.item}>
          <AppFailure failure={props.failure} locale={locale} />
          <AppTextField
            label={text.occurredOn}
            {...form.occurredOn}
            type="date"
            min="1900-01-01"
            max="2200-12-31"
            required
            error={expenseDraftFieldError(props.failure?.fields.occurredOn, locale)}
          />
          <Field
            label={text.category}
            validationState={props.failure?.fields.categoryId ? "error" : "none"}
            validationMessage={
              expenseDraftFieldError(props.failure?.fields.categoryId, locale) ?? null
            }
          >
            <div className="app-form-actions">
              <span>{view.category}</span>
              <AppButton appearance="subtle" onClick={form.openCategories}>
                {text.chooseCategory}
              </AppButton>
            </div>
          </Field>
          <AppTextField
            label={text.amount}
            {...form.amount}
            inputMode="decimal"
            maxLength={15}
            required
            hint={text.amountHint}
            error={expenseDraftFieldError(props.failure?.fields.amount, locale)}
          />
          <AppTextArea
            label={text.itemDescription}
            {...form.description}
            rows={2}
            maxLength={500}
            required
            error={expenseDraftFieldError(props.failure?.fields.description, locale)}
          />
          <Field label={text.costCenter}>
            <div className="app-form-actions">
              <span>{view.costCenter}</span>
              <AppButton appearance="subtle" onClick={form.openCostCenters}>
                {text.chooseCostCenter}
              </AppButton>
              {form.costCenterId && (
                <AppButton appearance="subtle" onClick={form.clearCostCenter}>
                  {text.clear}
                </AppButton>
              )}
            </div>
          </Field>
          <section className="app-resource-panel" aria-label={text.receipts}>
            <h2>{text.receipts}</h2>
            <p className="app-muted">{text.receiptHint}</p>
            {view.receipts.map((receipt) => (
              <div className="app-form-actions" key={receipt.id}>
                <span className="app-reference">{receipt.name}</span>
                {receipt.status && <span>{receipt.status}</span>}
                <AppButton
                  appearance="subtle"
                  icon={<Delete20Regular />}
                  aria-label={`${text.removeReceipt}: ${receipt.name}`}
                  onClick={() => form.removeReceipt(receipt.id)}
                />
              </div>
            ))}
            <AppButton
              appearance="subtle"
              icon={<Add20Regular />}
              onClick={form.openReceipts}
              disabled={form.receiptIds.length >= 3}
            >
              {text.addReceipt}
            </AppButton>
          </section>
          <div className="app-form-actions">
            <AppButton appearance="primary" type="submit">
              {text.applyItem}
            </AppButton>
            <AppButton onClick={form.cancel}>{text.cancel}</AppButton>
            {!props.creating && (
              <AppButton appearance="subtle" icon={<Delete20Regular />} onClick={form.remove}>
                {text.removeItem}
              </AppButton>
            )}
          </div>
        </form>
      )}
    </AppDetailsPanel>
  );
}
