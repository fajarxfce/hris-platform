import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import type { Failure } from "../../domain/result";
import { failureMessage } from "../i18n/failure-message";
import { type Locale, messages } from "../i18n/messages";

export function AppFailure({ failure, locale }: { failure: Failure | null; locale: Locale }) {
  if (!failure) return null;
  return (
    <MessageBar intent="error" role="alert">
      <MessageBarBody>
        {failureMessage(failure, locale)}
        {failure.correlationId && (
          <div className="app-reference">
            {messages(locale).reference}: {failure.correlationId}
          </div>
        )}
      </MessageBarBody>
    </MessageBar>
  );
}
