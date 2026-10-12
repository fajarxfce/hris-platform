import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useAttendanceCorrectionHistory } from "../controllers/use-attendance-correction-history";
import { attendanceHistoryMessages } from "../i18n/attendance-history-messages";
import { attendanceMessages } from "../i18n/attendance-messages";
import type { attendanceCorrectionHistoryView } from "../models/attendance-correction-history-view";

export function AttendanceCorrectionHistoryPage(props: {
  state: ReturnType<typeof useAttendanceCorrectionHistory>;
  view: ReturnType<typeof attendanceCorrectionHistoryView> | null;
  locale: Locale;
  companyName: string;
  backTo: string;
  firstPage: boolean;
  onFirst: () => void;
  onNext: () => void;
  onOpenChange: (version: string) => void;
  onCloseChange: () => void;
}) {
  const { state, view, locale } = props,
    text = attendanceHistoryMessages(locale);
  return (
    <>
      <section aria-busy={state.loading}>
        <AppPageHeader
          title={text.title}
          context={props.companyName}
          actions={
            <>
              <Link to={props.backTo}>{text.back}</Link>
              <AppButton
                appearance="subtle"
                icon={<ArrowClockwise20Regular />}
                onClick={state.refresh}
                disabled={state.loading}
              >
                {messages(locale).refresh}
              </AppButton>
            </>
          }
        />
        <AppFailure failure={state.failure} locale={locale} />
        {state.loading && <AppLoading label={messages(locale).loading} />}
        {view && (
          <div className="app-report-content">
            <AppPropertyList title={text.overview} items={view.properties} />
            {view.rows.length === 0 ? (
              <Text role="status">{text.empty}</Text>
            ) : (
              <AppResourceTable
                title={text.changes}
                columns={[
                  { id: "revision", label: text.revision, numeric: true },
                  { id: "recorded", label: text.recordedAt },
                  { id: "type", label: text.type },
                  { id: "reason", label: attendanceMessages(locale).reason },
                ]}
                rows={view.rows}
                action={{ label: text.view, onOpen: props.onOpenChange }}
              />
            )}
          </div>
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
            disabled={state.loading || state.history?.nextCursor == null}
          >
            {text.next}
          </AppButton>
        </nav>
      </section>
      <AppDetailsPanel
        open={view?.change != null}
        title={text.details}
        closeLabel={messages(locale).close}
        onClose={props.onCloseChange}
      >
        {view?.change && <AppPropertyList title={text.changes} items={view.change} />}
      </AppDetailsPanel>
    </>
  );
}
