import { Text } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppMetricCard } from "../../../../core/presentation/components/app-metric-card";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useEmployeeAttendance } from "../controllers/use-employee-attendance";
import { attendanceMessages } from "../i18n/attendance-messages";
import type { attendanceDayView } from "../models/attendance-view";

export function AttendanceDayPage(props: {
  state: ReturnType<typeof useEmployeeAttendance>;
  view: ReturnType<typeof attendanceDayView> | null;
  locale: Locale;
  companyName: string;
  date: string;
  backTo: string;
  correctionTo: string | null;
  onReview: ((id: string) => void) | null;
}) {
  const { state, view, locale } = props,
    text = attendanceMessages(locale);
  return (
    <section aria-busy={state.loading}>
      <AppPageHeader
        title={state.attendance?.employee.legalName ?? text.title}
        context={`${props.companyName} / ${props.date}`}
        actions={
          <>
            <Link to={props.backTo}>{text.back}</Link>
            <AppButton onClick={state.refresh} disabled={state.loading}>
              {messages(locale).refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.loading && <AppLoading label={messages(locale).loading} />}
      {view && (
        <div className="app-report-content">
          <div className="app-metric-grid">
            <AppMetricCard label={text.accepted} value={view.accepted} />
            <AppMetricCard label={text.pending} value={view.pending} />
          </div>
          {view.correction && <AppPropertyList title={text.correction} items={view.correction} />}
          {props.correctionTo && <Link to={props.correctionTo}>{text.correct}</Link>}
          {view.entries.length === 0 && <Text role="status">{text.noEvents}</Text>}
          {view.entries.map((entry) => (
            <section key={entry.id} aria-label={`${entry.kind} / ${entry.status}`}>
              <AppPropertyList title={`${entry.kind} / ${entry.status}`} items={entry.properties} />
              {entry.pending && props.onReview && (
                <AppButton onClick={() => props.onReview?.(entry.id)}>{text.review}</AppButton>
              )}
            </section>
          ))}
        </div>
      )}
    </section>
  );
}
