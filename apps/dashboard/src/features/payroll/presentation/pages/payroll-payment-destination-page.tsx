import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import type { Failure } from "../../../../core/domain/result";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { usePayrollPaymentDestinationForm } from "../controllers/use-payroll-payment-destination-form";
import { payrollPaymentMessages } from "../i18n/payroll-payment-messages";
import { payrollPaymentPreparationMessages } from "../i18n/payroll-payment-preparation-messages";
import type { payrollPaymentPreparationView } from "../models/payroll-payment-preparation-view";

export function PayrollPaymentDestinationPage(props: {
  form: ReturnType<typeof usePayrollPaymentDestinationForm>;
  item: NonNullable<ReturnType<typeof payrollPaymentPreparationView>["selected"]>;
  failure: Failure | null;
  locale: Locale;
}) {
  const { form, item, locale } = props,
    text = payrollPaymentPreparationMessages(locale),
    common = payrollPaymentMessages(locale);
  return (
    <AppDetailsPanel
      open
      title={text.destination}
      closeLabel={messages(locale).close}
      onClose={form.cancel}
    >
      <div className="app-report-content">
        <AppPropertyList title={common.itemDetails} items={item.properties} />
        <AppFailure failure={props.failure} locale={locale} />
        <form
          className="app-editor-form"
          aria-label={text.destination}
          onSubmit={form.submit}
          noValidate
          autoComplete="off"
        >
          <AppTextField
            label={common.bank}
            {...form.bankCode}
            maxLength={12}
            required
            hint={text.bankHint}
          />
          <AppTextField
            label={common.accountNumber}
            {...form.accountNumber}
            maxLength={34}
            inputMode="numeric"
            required
            hint={text.accountHint}
          />
          <AppTextField label={common.accountName} {...form.accountName} maxLength={120} required />
          {form.invalid && (
            <MessageBar intent="error" role="alert">
              <MessageBarBody>{text.destinationError}</MessageBarBody>
            </MessageBar>
          )}
          <div className="app-form-actions">
            <AppButton appearance="primary" type="submit">
              {text.saveDestination}
            </AppButton>
            <AppButton onClick={form.cancel}>{text.cancel}</AppButton>
            <AppButton appearance="subtle" onClick={form.remove}>
              {text.remove}
            </AppButton>
          </div>
        </form>
      </div>
    </AppDetailsPanel>
  );
}
