import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { payrollInputMessages } from "../i18n/payroll-input-messages";
import type { PayrollInputCommandState } from "../models/payroll-input-command-state";
export function PayrollInputCommandStatus({
  state,
  locale,
  backTo,
  savedText,
  onRetry,
  onRefresh,
}: {
  state: PayrollInputCommandState;
  locale: Locale;
  backTo: string;
  savedText: string;
  onRetry: () => void;
  onRefresh: () => void;
}) {
  const text = payrollInputMessages(locale);
  return (
    <>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.stage === "saving" && <AppLoading label={text.saving} />}
      {state.stage === "unavailable" && (
        <AppButton onClick={onRefresh}>{messages(locale).retry}</AppButton>
      )}
      {state.stage === "unconfirmed" && (
        <MessageBar intent="warning" layout="multiline" role="status">
          <MessageBarBody>
            {text.unconfirmed}
            <div className="app-reference">
              {text.operation}: {state.operationId}
            </div>
            <AppButton onClick={onRetry}>{text.retry}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "saved" && (
        <MessageBar intent="success" layout="multiline" role="status">
          <MessageBarBody>
            {savedText} {text.version}: {state.receipt?.version}.{" "}
            <Link to={backTo}>{text.view}</Link>
          </MessageBarBody>
        </MessageBar>
      )}
    </>
  );
}
