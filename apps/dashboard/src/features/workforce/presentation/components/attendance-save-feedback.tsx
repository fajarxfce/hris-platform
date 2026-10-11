import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { attendanceMessages } from "../i18n/attendance-messages";
import type { AttendanceEditorState } from "../models/attendance-editor-state";

export function AttendanceSaveFeedback(props: {
  state: AttendanceEditorState;
  locale: Locale;
  backTo: string;
  onRetry: () => void;
  onReload: () => void;
}) {
  const { state, locale } = props,
    text = attendanceMessages(locale);
  return (
    <>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.stage === "unavailable" && (
        <AppButton onClick={props.onReload}>{messages(locale).retry}</AppButton>
      )}
      {state.stage === "saving" && <AppLoading label={text.saving} />}
      {state.stage === "unconfirmed" && (
        <MessageBar intent="warning" role="status">
          <MessageBarBody>
            {text.unconfirmed}
            <div className="app-reference">
              {text.operation}: {state.operationId}
            </div>
            <AppButton onClick={props.onRetry}>{text.retry}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "saved" && (
        <MessageBar intent="success" role="status">
          <MessageBarBody>
            {text.saved} <Link to={props.backTo}>{text.view}</Link>
          </MessageBarBody>
        </MessageBar>
      )}
    </>
  );
}
