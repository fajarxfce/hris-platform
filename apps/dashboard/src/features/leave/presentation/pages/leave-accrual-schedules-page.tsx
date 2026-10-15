import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { leaveAccrualScheduleMessages } from "../i18n/leave-accrual-schedule-messages";
import { leaveBatchMessages } from "../i18n/leave-batch-messages";
import type { LeaveAccrualSchedulesState } from "../models/leave-accrual-schedule-state";
import type { leaveAccrualSchedulesView } from "../models/leave-accrual-schedule-view";
export function LeaveAccrualSchedulesPage(props: {
  state: LeaveAccrualSchedulesState;
  rows: ReturnType<typeof leaveAccrualSchedulesView>;
  locale: Locale;
  companyName: string;
  createTo: string | null;
  batchesTo: string;
  firstPage: boolean;
  onRefresh: () => void;
  onOpen: (id: string) => void;
  onFirst: () => void;
  onNext: () => void;
}) {
  const { state, locale } = props,
    text = leaveAccrualScheduleMessages(locale),
    batch = leaveBatchMessages(locale),
    shared = messages(locale);
  return (
    <section className="app-report-content" aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={props.companyName}
        actions={<Link to={props.batchesTo}>{text.processes}</Link>}
      />
      <AppCommandBar label={text.title}>
        {props.createTo && <Link to={props.createTo}>{text.create}</Link>}
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
      {state.stage === "loading" && !state.page && <AppLoading label={shared.loading} />}
      {state.page &&
        (props.rows.length ? (
          <AppResourceTable
            title={text.title}
            columns={[
              { id: "type", label: batch.type },
              { id: "frequency", label: batch.frequency },
              { id: "first", label: text.firstPeriod },
              { id: "periods", label: text.periods, numeric: true },
              { id: "employees", label: batch.employees, numeric: true },
              { id: "created", label: batch.createdAt },
            ]}
            rows={props.rows}
            action={{ label: text.view, columnId: "type", onOpen: props.onOpen }}
          />
        ) : (
          <p role="status">{text.empty}</p>
        ))}
      <fieldset className="app-pagination" aria-label={batch.pages}>
        <AppButton
          appearance="subtle"
          disabled={props.firstPage || state.stage === "loading"}
          onClick={props.onFirst}
        >
          {batch.first}
        </AppButton>
        <AppButton
          appearance="subtle"
          disabled={!state.page?.nextCursor || state.stage === "loading"}
          onClick={props.onNext}
        >
          {batch.next}
        </AppButton>
      </fieldset>
    </section>
  );
}
