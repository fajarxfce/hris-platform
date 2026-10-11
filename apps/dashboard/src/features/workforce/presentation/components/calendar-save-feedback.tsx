import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { calendarMessages } from "../i18n/calendar-messages";
import type { CalendarEditorState } from "../models/calendar-editor-state";

export function CalendarSaveFeedback({
  state,
  locale,
  calendarTo,
  onRetry,
  onReload,
}: {
  state: CalendarEditorState;
  locale: Locale;
  calendarTo: string;
  onRetry: () => void;
  onReload: () => void;
}) {
  const text = calendarMessages(locale);
  return (
    <>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.stage === "unavailable" && (
        <AppButton onClick={onReload}>{messages(locale).retry}</AppButton>
      )}
      {state.stage === "saving" && <AppLoading label={text.saving} />}
      {state.stage === "unconfirmed" && (
        <MessageBar intent="warning" role="status">
          <MessageBarBody>
            {text.unconfirmed}
            <div className="app-reference">
              {text.operation}: {state.operationId}
            </div>
            <AppButton onClick={onRetry}>{text.retrySave}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "saved" && (
        <MessageBar intent="success" role="status">
          <MessageBarBody>
            {text.saved} <Link to={calendarTo}>{text.view}</Link>
          </MessageBarBody>
        </MessageBar>
      )}
    </>
  );
}
