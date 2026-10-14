import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import type { Failure } from "../../../../core/domain/result";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCheckbox } from "../../../../core/presentation/components/app-checkbox";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { usePayrollPaymentOutcomeForm } from "../controllers/use-payroll-payment-outcome-form";
import { payrollPaymentMessages } from "../i18n/payroll-payment-messages";
import { payrollPaymentReconciliationMessages } from "../i18n/payroll-payment-reconciliation-messages";
import type { payrollPaymentReconciliationView } from "../models/payroll-payment-reconciliation-view";

export function PayrollPaymentOutcomePage(props: {
  form: ReturnType<typeof usePayrollPaymentOutcomeForm>;
  item: NonNullable<ReturnType<typeof payrollPaymentReconciliationView>["selected"]>;
  failure: Failure | null;
  timezone: string;
  locale: Locale;
}) {
  const { form, item, locale } = props,
    text = payrollPaymentReconciliationMessages(locale),
    common = payrollPaymentMessages(locale);
  return (
    <AppDetailsPanel
      open
      title={text.result}
      closeLabel={messages(locale).close}
      onClose={form.cancel}
    >
      <div className="app-report-content">
        <AppPropertyList title={common.itemDetails} items={item.properties} />
        <AppFailure failure={props.failure} locale={locale} />
        <form
          className="app-editor-form"
          aria-label={text.result}
          onSubmit={form.submit}
          noValidate
        >
          <AppSelect label={text.result} {...form.status} required>
            <option value="">{text.choose}</option>
            <option value="SUCCEEDED">{common.SUCCEEDED}</option>
            <option value="FAILED">{common.FAILED}</option>
          </AppSelect>
          {form.status.value === "SUCCEEDED" && (
            <AppTextField
              label={text.reference}
              {...form.reference}
              maxLength={100}
              required
              autoComplete="off"
            />
          )}
          <AppTextField
            label={`${text.occurred} (${props.timezone})`}
            {...form.occurredAt}
            type="datetime-local"
            step={1}
            required
          />
          <AppTextArea
            label={text.resultReason}
            {...form.reason}
            rows={3}
            maxLength={1000}
            required
          />
          {form.status.value === "FAILED" && (
            <AppCheckbox
              label={text.confirmation}
              name={form.confirmed.name}
              ref={form.confirmed.ref}
              onBlur={form.confirmed.onBlur}
              checked={form.confirmed.value}
              onChange={(_event, data) => form.confirmed.onChange(data.checked === true)}
              required
            />
          )}
          {form.invalid && (
            <MessageBar intent="error" role="alert">
              <MessageBarBody>{text.entryError}</MessageBarBody>
            </MessageBar>
          )}
          <div className="app-form-actions">
            <AppButton appearance="primary" type="submit">
              {item.original ? text.update : text.save}
            </AppButton>
            <AppButton onClick={form.cancel}>{text.cancel}</AppButton>
            {item.original && (
              <AppButton appearance="subtle" onClick={form.clear}>
                {text.clear}
              </AppButton>
            )}
          </div>
        </form>
      </div>
    </AppDetailsPanel>
  );
}
