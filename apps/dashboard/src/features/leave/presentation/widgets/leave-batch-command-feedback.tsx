import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { leaveBatchMessages } from "../i18n/leave-batch-messages";
import type { LeaveBatchStartState } from "../models/leave-batch-state";
export function LeaveBatchCommandFeedback(props: {
  stage: LeaveBatchStartState["stage"];
  operationId: string | null;
  locale: Locale;
  successTo: string | null;
  successMessage: string;
  onRetry: () => void;
}) {
  const text = leaveBatchMessages(props.locale);
  return (
    <>
      {props.stage === "saving" && <AppLoading label={text.saving} />}
      {props.stage === "unconfirmed" && (
        <MessageBar intent="warning" role="status" layout="multiline">
          <MessageBarBody>
            {text.unconfirmed}
            <div className="app-reference">
              {text.operation}: {props.operationId}
            </div>
            <AppButton appearance="subtle" onClick={props.onRetry}>
              {text.retry}
            </AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {props.stage === "saved" && (
        <MessageBar intent="success" role="status">
          <MessageBarBody>
            {props.successMessage}{" "}
            {props.successTo && <Link to={props.successTo}>{text.view}</Link>}
          </MessageBarBody>
        </MessageBar>
      )}
    </>
  );
}
