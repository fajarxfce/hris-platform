import { MessageBar, MessageBarBody, Text } from "@fluentui/react-components";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { usePayrollPaymentRecoveryForm } from "../controllers/use-payroll-payment-recovery-form";
import { payrollPaymentRecoveryMessages } from "../i18n/payroll-payment-recovery-messages";

export function PayrollPaymentRecoveryForm(props: {
  form: ReturnType<typeof usePayrollPaymentRecoveryForm>;
  locale: Locale;
  timezone: string;
}) {
  const { form, locale, timezone } = props,
    text = payrollPaymentRecoveryMessages(locale);
  return (
    <form
      className="app-editor-form app-payroll-return-form"
      aria-label={text.record}
      onSubmit={form.submit}
      noValidate
    >
      <Text>{text.note}</Text>
      {form.invalid && (
        <MessageBar intent="error">
          <MessageBarBody>{text.invalid}</MessageBarBody>
        </MessageBar>
      )}
      <div className="app-editor-fields">
        <AppTextField
          label={text.amount}
          {...form.amount}
          type="text"
          inputMode="decimal"
          required
          maxLength={15}
          readOnly={!form.editable}
        />
        <AppTextField
          label={text.reference}
          {...form.reference}
          required
          maxLength={100}
          readOnly={!form.editable}
        />
        <AppTextField
          label={`${text.occurred} (${timezone})`}
          {...form.occurredAt}
          type="datetime-local"
          step={1}
          required
          readOnly={!form.editable}
        />
      </div>
      <AppTextArea
        label={text.reason}
        {...form.reason}
        required
        maxLength={1000}
        rows={3}
        readOnly={!form.editable}
      />
      <div className="app-form-actions">
        <AppButton appearance="primary" type="submit" disabled={!form.editable}>
          {text.submit}
        </AppButton>
      </div>
    </form>
  );
}
