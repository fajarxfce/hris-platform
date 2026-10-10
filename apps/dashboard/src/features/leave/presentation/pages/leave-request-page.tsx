import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { leaveMessages } from "../i18n/leave-messages";
import type { LeaveRequestState } from "../models/leave-request-state";
import type { leaveRequestView } from "../models/leave-request-view";

export function LeaveRequestPage({
  state,
  view,
  actions,
  evidence,
  companyName,
  locale,
  timezone,
  backTo,
  firstHistory,
  onRefresh,
  onLatest,
  onOlder,
  onApproval,
}: {
  state: LeaveRequestState;
  view: ReturnType<typeof leaveRequestView> | null;
  actions: readonly Readonly<{ intent: string; label: string; to: string }>[];
  evidence: ReactNode;
  companyName: string;
  locale: Locale;
  timezone: string;
  backTo: string;
  firstHistory: boolean;
  onRefresh: () => void;
  onLatest: () => void;
  onOlder: () => void;
  onApproval: (id: string) => void;
}) {
  const text = leaveMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.details}
        context={companyName}
        actions={
          <>
            <Link to={backTo}>{text.back}</Link>
            {actions.map((action) => (
              <Link key={action.intent} to={action.to}>
                {action.label}
              </Link>
            ))}
            <AppButton disabled={state.stage === "loading"} onClick={onRefresh}>
              {messages(locale).refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      <div className="app-report-content app-leave-details">
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {view && (
          <>
            <AppPropertyList title={text.summary} items={view.properties} />
            <AppResourceTable
              title={`${text.schedule} (${timezone})`}
              columns={[
                { id: "date", label: text.date },
                { id: "portion", label: text.portion },
                { id: "start", label: text.start },
                { id: "end", label: text.end },
                { id: "planned", label: text.planned, numeric: true },
                { id: "charged", label: text.charged, numeric: true },
              ]}
              rows={view.days}
            />
            <AppPropertyList title={text.policy} items={view.policy} />
            {view.workflows.map((workflow) => (
              <section key={workflow.id} className="app-leave-workflow" aria-label={workflow.title}>
                <p>
                  {text.status}: {workflow.status}
                </p>
                <AppButton
                  onClick={() => onApproval(workflow.id)}
                  aria-label={`${text.approval}: ${workflow.title}`}
                >
                  {text.approval}
                </AppButton>
                <AppResourceTable
                  title={workflow.title}
                  columns={[
                    { id: "stage", label: text.stage, numeric: true },
                    { id: "assignees", label: text.assignees },
                    { id: "current", label: text.active },
                  ]}
                  rows={workflow.rows}
                />
              </section>
            ))}
            {evidence}
            <AppResourceTable
              title={text.history}
              columns={[
                { id: "version", label: text.version, numeric: true },
                { id: "change", label: text.change },
                { id: "status", label: text.status },
                { id: "recorded", label: `${text.recorded} (${timezone})` },
                { id: "actor", label: text.actor },
                { id: "reason", label: text.reason },
              ]}
              rows={view.history}
            />
            <fieldset className="app-pagination" aria-label={text.historyPages}>
              <AppButton onClick={onLatest} disabled={firstHistory}>
                {text.latest}
              </AppButton>
              <AppButton onClick={onOlder} disabled={state.request?.history.nextCursor == null}>
                {text.older}
              </AppButton>
            </fieldset>
          </>
        )}
      </div>
    </section>
  );
}
