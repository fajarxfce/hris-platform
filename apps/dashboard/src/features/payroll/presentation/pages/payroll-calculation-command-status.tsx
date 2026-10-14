import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { payrollCalculationMessages } from "../i18n/payroll-calculation-messages";
import type { PayrollCalculationCommandState } from "../models/payroll-calculation-command-state";
export function PayrollCalculationCommandStatus(props: {
  state: PayrollCalculationCommandState<unknown>;
  locale: Locale;
  successMessage: string;
  successTo: string | null;
  onRetry: () => void;
  onReload: () => void;
}) {
  const { state, locale } = props,
    text = payrollCalculationMessages(locale);
  return (
    <>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.stage === "saving" && <AppLoading label={text.saving} />}
      {state.stage === "saved" && (
        <MessageBar intent="success" role="status">
          <MessageBarBody>
            {props.successMessage}{" "}
            {props.successTo && <Link to={props.successTo}>{text.openRun}</Link>}
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "unconfirmed" && (
        <MessageBar intent="warning" role="status">
          <MessageBarBody>
            {text.unconfirmed} <AppButton onClick={props.onRetry}>{text.retry}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "conflict" && <p role="status">{text.conflict}</p>}
      {(state.stage === "conflict" || state.stage === "unavailable") && (
        <div>
          <AppButton onClick={props.onReload}>{text.reload}</AppButton>
        </div>
      )}
    </>
  );
}
