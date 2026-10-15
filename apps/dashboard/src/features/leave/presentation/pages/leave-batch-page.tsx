import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppMetricCard } from "../../../../core/presentation/components/app-metric-card";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { leaveBatchMessages } from "../i18n/leave-batch-messages";
import type { LeaveBatchState } from "../models/leave-batch-state";
import type { leaveBatchView } from "../models/leave-batch-view";
export function LeaveBatchPage(props: {
  state: LeaveBatchState;
  view: ReturnType<typeof leaveBatchView> | null;
  locale: Locale;
  companyName: string;
  backTo: string;
  resumeTo: string | null;
  jobTo: string | null;
  firstPage: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onLedger: (id: string) => void;
}) {
  const { state, view, locale } = props,
    text = leaveBatchMessages(locale),
    shared = messages(locale);
  return (
    <section className="app-report-content" aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.detail}
        context={props.companyName}
        actions={<Link to={props.backTo}>{text.back}</Link>}
      />
      <AppCommandBar label={text.detail}>
        {props.resumeTo && <Link to={props.resumeTo}>{text.resume}</Link>}
        {props.jobTo && <Link to={props.jobTo}>{text.jobLink}</Link>}
        <AppButton
          appearance="subtle"
          icon={<ArrowClockwise20Regular />}
          onClick={props.onRefresh}
          disabled={state.stage === "loading"}
        >
          {shared.refresh}
        </AppButton>
      </AppCommandBar>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={shared.loading} />}
      {state.updatesPaused && (
        <MessageBar intent="info" role="status">
          <MessageBarBody>{text.paused}</MessageBarBody>
        </MessageBar>
      )}
      {view && (
        <>
          <AppPropertyList title={text.overview} items={view.properties} />
          <div className="app-metric-grid">
            {view.metrics.map((metric) => (
              <AppMetricCard key={metric.label} {...metric} />
            ))}
          </div>
          <AppFailure failure={view.issue} locale={locale} />
          <p className="app-muted">{text.progressNote}</p>
          {view.rows.length ? (
            <AppResourceTable
              title={text.results}
              columns={[
                { id: "employee", label: text.employee },
                { id: "number", label: text.number },
                { id: "status", label: text.result },
                { id: "reason", label: text.reason },
              ]}
              rows={view.rows}
              action={{
                label: text.ledger,
                columnId: "employee",
                onOpen: props.onLedger,
                disabled: state.failure !== null,
              }}
            />
          ) : (
            <p role="status">{text.noResults}</p>
          )}
          <fieldset className="app-pagination" aria-label={text.pages}>
            <AppButton
              appearance="subtle"
              disabled={props.firstPage || state.stage === "loading"}
              onClick={props.onFirst}
            >
              {text.first}
            </AppButton>
            <AppButton
              appearance="subtle"
              disabled={
                !state.details?.results.nextCursor ||
                state.stage === "loading" ||
                state.failure !== null
              }
              onClick={props.onNext}
            >
              {text.next}
            </AppButton>
          </fieldset>
          <AppResourceTable
            title={text.attempts}
            columns={[
              { id: "attempt", label: text.attempt, numeric: true },
              { id: "startedAt", label: text.startedAt },
              { id: "completed", label: text.baseCompleted, numeric: true },
              { id: "reason", label: text.reason },
            ]}
            rows={view.attempts}
          />
          <details>
            <summary>{text.policy}</summary>
            <AppPropertyList title={text.policy} items={view.policy} />
          </details>
          <details>
            <summary>{text.references}</summary>
            <AppPropertyList title={text.references} items={view.references} />
          </details>
        </>
      )}
    </section>
  );
}
