import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useAttendanceAdjustment } from "../controllers/use-attendance-adjustment";
import { attendanceAdjustmentMessages } from "../i18n/attendance-adjustment-messages";
import type { workPeriodSnapshotView } from "../models/work-period-view";

export function AttendanceAdjustmentDetailsPage(props: {
  state: ReturnType<typeof useAttendanceAdjustment>;
  view: ReturnType<typeof workPeriodSnapshotView> | null;
  properties: readonly { label: string; value: string }[];
  locale: Locale;
  companyName: string;
  backTo: string;
  originalTo: string;
}) {
  const { state, locale, view } = props,
    text = attendanceAdjustmentMessages(locale);
  return (
    <section className="app-report-content" aria-busy={state.loading}>
      <AppPageHeader
        title={text.details}
        context={`${props.companyName} / ${state.details?.snapshot.employee.legalName ?? text.title}`}
        actions={<Link to={props.backTo}>{text.backHistory}</Link>}
      />
      <div className="app-command-bar">
        <Link to={props.originalTo}>{text.original}</Link>
        <AppButton
          appearance="subtle"
          icon={<ArrowClockwise20Regular />}
          disabled={state.loading}
          onClick={state.refresh}
        >
          {messages(locale).refresh}
        </AppButton>
      </div>
      <AppFailure failure={state.failure} locale={locale} />
      {state.loading && <AppLoading label={messages(locale).loading} />}
      {state.details && view && (
        <>
          <AppPropertyList title={text.details} items={props.properties} />
          <Text className="app-muted">{text.retainedHint}</Text>
          <AppResourceTable
            title={text.snapshot}
            columns={[
              { id: "date", label: text.date },
              { id: "fact", label: text.status },
              { id: "schedule", label: text.schedule },
              { id: "minutes", label: text.minutes, numeric: true },
              { id: "overtime", label: text.overtime, numeric: true },
            ]}
            rows={view.days}
          />
        </>
      )}
    </section>
  );
}
