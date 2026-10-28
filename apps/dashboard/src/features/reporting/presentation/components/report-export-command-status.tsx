import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { reportExportMessages } from "../i18n/report-export-messages";
import type { ReportExportState } from "../models/report-export-state";
export function ReportExportCommandStatus({
  state,
  locale,
  openTo,
  onRetry,
}: {
  state: ReportExportState;
  locale: Locale;
  openTo: string | null;
  onRetry: () => void;
}) {
  const text = reportExportMessages(locale);
  return (
    <>
      {state.stage === "unconfirmed" && (
        <MessageBar layout="multiline" intent="warning" role="status">
          <MessageBarBody>
            {text.unconfirmed}
            <div className="app-reference">
              {text.operation}: {state.operationId}
            </div>
            <AppButton onClick={onRetry}>{text.retry}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.receipt && (
        <MessageBar layout="multiline" intent="success" role="status">
          <MessageBarBody>
            {text.accepted} {openTo && <Link to={openTo}>{text.open}</Link>}
          </MessageBarBody>
        </MessageBar>
      )}
    </>
  );
}
