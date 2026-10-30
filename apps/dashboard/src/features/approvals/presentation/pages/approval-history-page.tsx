import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { approvalHistoryMessages } from "../i18n/approval-history-messages";
import { approvalMessages } from "../i18n/approval-messages";
import type { ApprovalHistoryState } from "../models/approval-history-state";
import type { approvalHistoryView } from "../models/approval-history-view";
export function ApprovalHistoryPage(props: {
  state: ApprovalHistoryState;
  view: ReturnType<typeof approvalHistoryView> | null;
  locale: Locale;
  companyName: string;
  timezone: string;
  backTo: string;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
}) {
  const text = approvalHistoryMessages(props.locale),
    common = messages(props.locale),
    view = props.view;
  const ready = props.state.stage === "ready";
  return (
    <section aria-busy={props.state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={props.companyName}
        actions={
          <>
            <Link to={props.backTo}>{text.back}</Link>
            <AppButton onClick={props.onRefresh} disabled={props.state.stage === "loading"}>
              {common.refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={props.state.failure} locale={props.locale} />
      {props.state.stage === "loading" && <AppLoading label={common.loading} />}
      {view && (
        <div className="app-report-content">
          <AppPropertyList title={approvalMessages(props.locale).summary} items={view.properties} />
          <AppResourceTable
            title={`${text.decisions} · ${props.timezone}`}
            columns={[
              { id: "step", label: text.step, numeric: true },
              { id: "decision", label: text.decision },
              { id: "actor", label: text.actor },
              { id: "decidingFor", label: text.decidingFor },
              { id: "time", label: text.time },
              { id: "reason", label: text.reason },
            ]}
            rows={view.decisions}
          />
          {view.decisions.length === 0 && <p>{text.emptyDecisions}</p>}
          <AppResourceTable
            title={`${text.reassignments} · ${props.timezone}`}
            columns={[
              { id: "revision", label: text.revision, numeric: true },
              { id: "step", label: text.step, numeric: true },
              { id: "assignees", label: text.assignees },
              { id: "actor", label: text.actor },
              { id: "time", label: text.time },
              { id: "reason", label: text.reason },
            ]}
            rows={view.reassignments}
          />
          {view.reassignments.length === 0 && <p>{text.emptyChanges}</p>}
          <nav aria-label={text.pages}>
            <AppButton disabled={!ready || props.state.after === null} onClick={props.onFirst}>
              {text.first}
            </AppButton>
            <AppButton
              disabled={!ready || !props.state.history?.reassignments.nextCursor}
              onClick={props.onNext}
            >
              {text.next}
            </AppButton>
          </nav>
        </div>
      )}
    </section>
  );
}
