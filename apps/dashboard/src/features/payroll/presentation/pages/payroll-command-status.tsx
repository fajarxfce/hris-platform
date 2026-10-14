import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { payrollReviewMessages } from "../i18n/payroll-review-messages";
import type { PayrollCommandState } from "../models/payroll-command-state";
export function PayrollCommandStatus(props: {
  state: PayrollCommandState<unknown>;
  locale: Locale;
  successMessage: string;
  successLabel: string;
  successTo: string | null;
  onRetry: () => void;
  onReload: () => void;
}) {
  const { state, locale } = props,
    text = payrollReviewMessages(locale);
  return (
    <>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.stage === "saving" && <AppLoading label={text.saving} />}
      {state.stage === "saved" && (
        <MessageBar intent="success" role="status">
          <MessageBarBody>
            {props.successMessage}{" "}
            {props.successTo && <Link to={props.successTo}>{props.successLabel}</Link>}
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
