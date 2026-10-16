import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useAttendanceAdjustments } from "../controllers/use-attendance-adjustments";
import { attendanceAdjustmentMessages } from "../i18n/attendance-adjustment-messages";

export function AttendanceAdjustmentHistoryPage(props: {
  state: ReturnType<typeof useAttendanceAdjustments>;
  locale: Locale;
  companyName: string;
  month: string;
  backTo: string;
  firstPage: boolean;
  rows: readonly { id: string; actionLabel: string; cells: readonly string[] }[];
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const { state, locale } = props,
    text = attendanceAdjustmentMessages(locale);
  return (
    <section className="app-report-content" aria-busy={state.loading}>
      <AppPageHeader
        title={text.history}
        context={`${props.companyName} / ${state.history?.employee.legalName ?? props.month}`}
        actions={<Link to={props.backTo}>{text.back}</Link>}
      />
      <div className="app-command-bar">
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
      {state.history && (
        <>
          <Text>{props.month}</Text>
          {props.rows.length === 0 ? (
            <Text role="status">{text.empty}</Text>
          ) : (
            <AppResourceTable
              title={text.history}
              columns={[
                { id: "revision", label: text.revision, numeric: true },
                { id: "date", label: text.date },
                { id: "recorded", label: text.recorded },
                { id: "reason", label: text.reason },
              ]}
              rows={props.rows}
              action={{ label: text.view, onOpen: props.onOpen, columnId: "revision" }}
            />
          )}
          <nav className="app-pagination" aria-label={text.pages}>
            <AppButton
              appearance="subtle"
              onClick={props.onFirst}
              disabled={state.loading || props.firstPage}
            >
              {text.first}
            </AppButton>
            <AppButton
              appearance="subtle"
              onClick={props.onNext}
              disabled={state.loading || state.history.nextCursor === null}
            >
              {text.next}
            </AppButton>
          </nav>
        </>
      )}
    </section>
  );
}
