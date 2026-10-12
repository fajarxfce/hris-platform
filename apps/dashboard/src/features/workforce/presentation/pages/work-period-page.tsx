import { Badge, ProgressBar, Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular, CalendarCheckmark20Regular } from "@fluentui/react-icons";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useWorkPeriodMonth } from "../controllers/use-work-period-month";
import { workPeriodMessages } from "../i18n/work-period-messages";
import type { WorkPeriodState } from "../models/work-period-state";
import type { workPeriodOverviewView } from "../models/work-period-view";

export function WorkPeriodPage(props: {
  state: WorkPeriodState;
  view: ReturnType<typeof workPeriodOverviewView> | null;
  filters: ReturnType<typeof useWorkPeriodMonth>;
  locale: Locale;
  companyName: string;
  jobTo: string | null;
  onClose: () => void;
  onRecover: () => void;
  onRefresh: () => void;
  children: ReactNode;
}) {
  const { state, view, filters, locale } = props,
    text = workPeriodMessages(locale);
  return (
    <section className="app-work-period" aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={props.companyName}
        actions={
          <>
            {state.overview?.availableActions.includes("close") && (
              <AppButton
                appearance="primary"
                icon={<CalendarCheckmark20Regular />}
                onClick={props.onClose}
              >
                {text.close}
              </AppButton>
            )}
            {state.overview?.availableActions.includes("recover") && (
              <AppButton appearance="primary" onClick={props.onRecover}>
                {text.recover}
              </AppButton>
            )}
            <AppButton
              appearance="subtle"
              icon={<ArrowClockwise20Regular />}
              disabled={state.stage === "loading" || state.stage === "updating"}
              onClick={props.onRefresh}
            >
              {messages(locale).refresh}
            </AppButton>
          </>
        }
      />
      <form onSubmit={filters.apply} noValidate>
        <AppCommandBar label={text.selectMonth}>
          <div className="app-resource-filters">
            <AppTextField
              label={text.month}
              type="month"
              min="2000-01"
              max="2100-12"
              {...filters.month}
              error={filters.error}
            />
            <AppButton type="submit">{text.apply}</AppButton>
          </div>
        </AppCommandBar>
      </form>
      <div className="app-report-content">
        <AppFailure failure={state.failure} locale={locale} />
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {view && (
          <>
            <section
              className="app-resource-panel app-work-period-summary"
              aria-label={text.overview}
            >
              <div className="app-work-period-heading">
                <h2>{view.month}</h2>
                <Badge appearance="tint" color={view.color}>
                  {view.status}
                </Badge>
                {props.jobTo && <Link to={props.jobTo}>{text.job}</Link>}
              </div>
              {view.progress ? (
                <div className="app-work-period-progress">
                  <div className="app-work-period-heading">
                    <Text>{text.processed}</Text>
                    <Text weight="semibold">{view.progress.label}</Text>
                    <Text className="app-muted">{view.progress.status}</Text>
                  </div>
                  <ProgressBar aria-label={text.progress} value={view.progress.value} max={1} />
                  {state.overview?.attempt?.cancellationRequested && (
                    <Text>{text.cancellation}</Text>
                  )}
                  {state.updatesPaused && <Text role="status">{text.paused}</Text>}
                </div>
              ) : (
                <Text>{view.notEnded ? text.notEnded : text.notStarted}</Text>
              )}
            </section>
            <AppFailure failure={view.failure} locale={locale} />
            <AppPropertyList title={text.details} items={view.properties} />
            {props.children}
          </>
        )}
      </div>
    </section>
  );
}
