import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import { useContext } from "react";
import type { Failure } from "../../domain/result";
import { WorkspaceSessionContext } from "../contracts/workspace-session";
import { failureMessage } from "../i18n/failure-message";
import { type Locale, messages } from "../i18n/messages";
import { AppButton } from "./app-button";

export function AppFailure({ failure, locale }: { failure: Failure | null; locale: Locale }) {
  const requestVerification = useContext(WorkspaceSessionContext)?.verifyAccount;
  const restoreFocus = useRestoreFocusTarget();
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
        {requestVerification &&
          ["mfa_required", "mfa_setup_required", "recent_authentication_required"].includes(
            failure.code,
          ) && (
            <div>
              <AppButton {...restoreFocus} onClick={requestVerification}>
                {messages(locale).verification}
              </AppButton>
            </div>
          )}
      </MessageBarBody>
    </MessageBar>
  );
}
