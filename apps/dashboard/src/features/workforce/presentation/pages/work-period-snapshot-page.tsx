import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppMetricCard } from "../../../../core/presentation/components/app-metric-card";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useWorkPeriodSnapshot } from "../controllers/use-work-period-snapshot";
import { attendanceAdjustmentMessages } from "../i18n/attendance-adjustment-messages";
import { workPeriodMessages } from "../i18n/work-period-messages";
import type { workPeriodSnapshotView } from "../models/work-period-view";

export function WorkPeriodSnapshotPage(props: {
  state: ReturnType<typeof useWorkPeriodSnapshot>;
  view: ReturnType<typeof workPeriodSnapshotView> | null;
  locale: Locale;
  companyName: string;
  backTo: string | null;
  adjustmentsTo: string | null;
  onReviewDay: ((date: string) => void) | null;
}) {
  const { state, view, locale } = props,
    text = workPeriodMessages(locale);
  return (
    <section className="app-report-content app-work-period-result" aria-busy={state.loading}>
      <AppPageHeader
        title={state.snapshot?.employee.legalName ?? text.result}
        context={props.companyName}
        actions={
          <>
            {props.backTo && <Link to={props.backTo}>{text.back}</Link>}
            {props.adjustmentsTo && (
              <Link to={props.adjustmentsTo}>{attendanceAdjustmentMessages(locale).history}</Link>
            )}
            <AppButton
              appearance="subtle"
              icon={<ArrowClockwise20Regular />}
              disabled={state.loading}
              onClick={state.refresh}
            >
              {messages(locale).refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.loading && <AppLoading label={messages(locale).loading} />}
      {view && (
        <>
          <AppPropertyList title={text.result} items={view.properties} />
          <div className="app-metric-grid">
            {view.metrics.map((metric) => (
              <AppMetricCard key={metric.label} {...metric} />
            ))}
          </div>
          <Text className="app-muted">{text.resultsHint}</Text>
          <AppResourceTable
            title={text.results}
            columns={[
              { id: "date", label: text.date },
              { id: "fact", label: text.fact },
              { id: "schedule", label: text.schedule },
              { id: "minutes", label: text.accepted, numeric: true },
              { id: "overtime", label: text.overtime, numeric: true },
            ]}
            rows={view.days}
            {...(props.onReviewDay
              ? {
                  action: {
                    label: attendanceAdjustmentMessages(locale).review,
                    onOpen: props.onReviewDay,
                    columnId: "date",
                  },
                }
              : {})}
          />
        </>
      )}
    </section>
  );
}
