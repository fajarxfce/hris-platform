import { ProgressBar } from "@fluentui/react-components";
import { ArrowSync20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppMetricCard } from "../../../../core/presentation/components/app-metric-card";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTabs } from "../../../../core/presentation/components/app-tabs";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { payrollCalculationMessages } from "../i18n/payroll-calculation-messages";
import { payrollComparisonMessages } from "../i18n/payroll-comparison-messages";
import { payrollFinalizationMessages } from "../i18n/payroll-finalization-messages";
import { payrollReviewMessages } from "../i18n/payroll-review-messages";
import type { PayrollRunState } from "../models/payroll-calculation-state";
import type { payrollRunView } from "../models/payroll-calculation-view";
export function PayrollRunPage(props: {
  state: PayrollRunState;
  view: ReturnType<typeof payrollRunView> | null;
  locale: Locale;
  companyName: string;
  backTo: string | null;
  comparisonTo?: string | null;
  finalizationsTo: string | null;
  jobTo: string | null;
  reviewsTo: string | null;
  resumeTo: string | null;
  abandonTo: string | null;
  tab: string;
  firstPage: boolean;
  onRefresh: () => void;
  onTab: (tab: string) => void;
  onEmployee: (id: string) => void;
  onFirst: () => void;
  onNext: () => void;
}) {
  const { state, view, locale } = props,
    text = payrollCalculationMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={state.details ? `${text.run} ${state.details.run.number}` : text.run}
        context={props.companyName}
        actions={props.backTo && <Link to={props.backTo}>{text.calculations}</Link>}
      />
      <AppCommandBar label={text.run}>
        <AppButton
          appearance="subtle"
          icon={<ArrowSync20Regular />}
          onClick={props.onRefresh}
          disabled={state.stage === "loading" || state.stage === "updating"}
        >
          {messages(locale).refresh}
        </AppButton>
        {props.comparisonTo && (
          <Link to={props.comparisonTo}>{payrollComparisonMessages(locale).title}</Link>
        )}
        {props.reviewsTo && (
          <Link to={props.reviewsTo}>{payrollReviewMessages(locale).reviews}</Link>
        )}
        {props.finalizationsTo && (
          <Link to={props.finalizationsTo}>{payrollFinalizationMessages(locale).title}</Link>
        )}
        {props.jobTo && <Link to={props.jobTo}>{text.job}</Link>}
        {props.resumeTo && <Link to={props.resumeTo}>{text.resume}</Link>}
        {props.abandonTo && <Link to={props.abandonTo}>{text.abandon}</Link>}
      </AppCommandBar>
      <div className="app-report-content">
        <AppFailure failure={state.failure} locale={locale} />
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {view && state.details && (
          <>
            <div className="app-metric-grid">
              {view.metrics.map((metric) => (
                <AppMetricCard key={metric.label} {...metric} />
              ))}
            </div>
            <section className="app-resource-panel" aria-label={text.progress}>
              <h2>{text.progress}</h2>
              <p>
                {view.progressLabel} · {text.jobStatuses[state.details.job.status]}
              </p>
              <ProgressBar aria-label={text.progress} value={view.progress} max={1} />
              {state.details.job.cancellationRequested && <p>{text.cancellation}</p>}
              {state.updatesPaused && <p role="status">{text.paused}</p>}
            </section>
            <AppFailure failure={view.failure} locale={locale} />
            <p className="app-muted">{text.paymentNote}</p>
            <AppTabs
              id="payroll-run"
              label={text.run}
              tabs={[
                { id: "results", label: text.results },
                { id: "attempts", label: text.attempts },
                { id: "sources", label: text.sources },
              ]}
              selected={props.tab}
              onSelected={props.onTab}
            />
            <div
              role="tabpanel"
              id="payroll-run-panel"
              aria-labelledby={`payroll-run-tab-${props.tab}`}
            >
              {props.tab === "results" && (
                <>
                  {view.results.length === 0 ? (
                    <p>{text.noResults}</p>
                  ) : (
                    <AppResourceTable
                      title={text.results}
                      columns={[
                        { id: "number", label: text.employeeNumber },
                        { id: "name", label: text.name },
                        { id: "result", label: text.status },
                        { id: "gross", label: text.taxableGross, numeric: true },
                        { id: "tax", label: text.withheld, numeric: true },
                        { id: "net", label: text.takeHome, numeric: true },
                      ]}
                      rows={view.results}
                      action={{ label: text.openResult, onOpen: props.onEmployee }}
                    />
                  )}
                  <fieldset className="app-pagination" aria-label={text.pages}>
                    <AppButton disabled={props.firstPage} onClick={props.onFirst}>
                      {text.first}
                    </AppButton>
                    <AppButton
                      disabled={state.details.results.nextCursor === null}
                      onClick={props.onNext}
                    >
                      {text.next}
                    </AppButton>
                  </fieldset>
                </>
              )}
              {props.tab === "attempts" && (
                <AppResourceTable
                  title={text.attempts}
                  columns={[
                    { id: "number", label: text.number },
                    { id: "started", label: text.created },
                    { id: "base", label: text.baseCompleted, numeric: true },
                    { id: "reason", label: text.reason },
                    { id: "job", label: text.jobId },
                  ]}
                  rows={view.attempts}
                />
              )}
              {props.tab === "sources" && (
                <AppPropertyList title={text.sources} items={view.properties} />
              )}
            </div>
          </>
        )}
      </div>
    </section>
  );
}
