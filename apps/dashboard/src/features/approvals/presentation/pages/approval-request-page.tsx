import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { approvalMessages } from "../i18n/approval-messages";
import { approvalReassignmentMessages } from "../i18n/approval-reassignment-messages";
import type { ApprovalRequestState } from "../models/approval-request-state";
import type { approvalRequestView } from "../models/approval-view";

export function ApprovalRequestPage({
  state,
  view,
  companyName,
  locale,
  backTo,
  reassignTo,
  resourceTo,
  onRefresh,
}: {
  state: ApprovalRequestState;
  view: ReturnType<typeof approvalRequestView> | null;
  companyName: string;
  locale: Locale;
  backTo: string;
  reassignTo: string | null;
  resourceTo: string | null;
  onRefresh: () => void;
}) {
  const text = approvalMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.details}
        context={companyName}
        actions={
          <>
            <Link to={backTo}>{text.back}</Link>
            {resourceTo && <Link to={resourceTo}>{text.sourceRequest}</Link>}
            {reassignTo && (
              <Link to={reassignTo}>{approvalReassignmentMessages(locale).action}</Link>
            )}
            <AppButton disabled={state.stage === "loading"} onClick={onRefresh}>
              {messages(locale).refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      <div className="app-report-content">
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.request?.status === "BLOCKED" && (
          <MessageBar intent="warning" layout="multiline">
            <MessageBarBody>{text.blocked}</MessageBarBody>
          </MessageBar>
        )}
        {view && (
          <>
            <AppPropertyList title={text.summary} items={view.properties} />
            <AppResourceTable
              title={text.stages}
              columns={[
                { id: "step", label: text.step, numeric: true },
                { id: "assignees", label: text.assignees },
                { id: "status", label: text.status },
              ]}
              rows={view.stages}
            />
          </>
        )}
      </div>
    </section>
  );
}
