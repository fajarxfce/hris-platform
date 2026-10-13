import { ArrowDownload20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { expensePaymentMessages } from "../i18n/expense-payment-messages";
import type { ExpensePaymentDownloadState } from "../models/expense-payment-download-state";
export function ExpensePaymentDownload(props: {
  state: ExpensePaymentDownloadState;
  locale: Locale;
  onDownload: () => void;
  onCancel: () => void;
}) {
  const text = expensePaymentMessages(props.locale);
  return (
    <div>
      <div className="app-form-actions">
        <AppButton
          appearance="primary"
          icon={<ArrowDownload20Regular />}
          disabled={props.state.stage === "downloading" || props.state.stage === "unavailable"}
          onClick={props.onDownload}
        >
          {text.download}
        </AppButton>
        {props.state.stage === "downloading" && (
          <>
            <span role="status">{text.downloading}</span>
            <AppButton appearance="subtle" onClick={props.onCancel}>
              {text.cancelDownload}
            </AppButton>
          </>
        )}
        {props.state.stage === "started" && <span role="status">{text.downloaded}</span>}
      </div>
      <AppFailure failure={props.state.failure} locale={props.locale} />
    </div>
  );
}
