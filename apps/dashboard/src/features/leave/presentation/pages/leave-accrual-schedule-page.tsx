import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { leaveAccrualScheduleMessages } from "../i18n/leave-accrual-schedule-messages";
import { leaveBatchMessages } from "../i18n/leave-batch-messages";
import type { LeaveAccrualScheduleState } from "../models/leave-accrual-schedule-state";
import type { leaveAccrualScheduleView } from "../models/leave-accrual-schedule-view";
export function LeaveAccrualSchedulePage(props: {
  state: LeaveAccrualScheduleState;
  view: ReturnType<typeof leaveAccrualScheduleView> | null;
  locale: Locale;
  companyName: string;
  backTo: string;
  onRefresh: () => void;
  onOpenPeriod: (id: string) => void;
}) {
  const { state, view, locale } = props,
    text = leaveAccrualScheduleMessages(locale),
    batch = leaveBatchMessages(locale),
    shared = messages(locale);
  return (
    <section className="app-report-content" aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.detail}
        context={props.companyName}
        actions={<Link to={props.backTo}>{text.back}</Link>}
      />
      <AppCommandBar label={text.detail}>
        <AppButton
          appearance="subtle"
          icon={<ArrowClockwise20Regular />}
          disabled={state.stage === "loading"}
          onClick={props.onRefresh}
        >
          {shared.refresh}
        </AppButton>
      </AppCommandBar>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && !view && <AppLoading label={shared.loading} />}
      {view && (
        <>
          <AppPropertyList title={text.detail} items={view.properties} />
          <AppResourceTable
            title={text.periods}
            columns={[
              { id: "period", label: batch.period },
              { id: "due", label: text.due },
              { id: "status", label: batch.status },
              { id: "processed", label: text.processed, numeric: true },
            ]}
            rows={view.rows}
            action={{ label: text.progress, columnId: "period", onOpen: props.onOpenPeriod }}
          />
          <p className="app-muted">{text.historyNote}</p>
          <details>
            <summary>{text.references}</summary>
            <AppPropertyList title={text.references} items={view.references} />
          </details>
        </>
      )}
    </section>
  );
}
