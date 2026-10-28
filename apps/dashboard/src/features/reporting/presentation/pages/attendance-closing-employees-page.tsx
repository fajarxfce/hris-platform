import { Text } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { attendanceClosingReportMessages } from "../i18n/attendance-closing-report-messages";
import type { AttendanceClosingEmployeesState } from "../models/attendance-closing-employees-state";
import type { attendanceClosingEmployeesView } from "../models/attendance-closing-report-view";
export function AttendanceClosingEmployeesPage(props: {
  state: AttendanceClosingEmployeesState;
  rows: ReturnType<typeof attendanceClosingEmployeesView>;
  locale: Locale;
  companyName: string;
  month: string;
  backTo: string;
  firstPage: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const text = attendanceClosingReportMessages(props.locale),
    loading = props.state.stage === "loading";
  return (
    <section className="app-report-content" aria-busy={loading}>
      <AppPageHeader
        title={text.employeesTitle}
        context={`${props.companyName} / ${props.month}`}
        actions={
          <>
            <Link to={props.backTo}>{text.back}</Link>
            <AppButton onClick={props.onRefresh} disabled={loading}>
              {messages(props.locale).refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={props.state.failure} locale={props.locale} />
      {loading && <AppLoading label={messages(props.locale).loading} />}
      <Text className="app-muted">{text.employeeDefinition}</Text>
      {props.state.page &&
        (props.rows.length ? (
          <AppResourceTable
            title={text.employeesTitle}
            columns={[
              { id: "number", label: text.number },
              { id: "name", label: text.name },
              { id: "revision", label: text.revision },
              ...(
                ["originalMinutes", "currentMinutes", "originalAbsence", "currentAbsence"] as const
              ).map((id) => ({ id, label: text[id], numeric: true })),
            ]}
            rows={props.rows}
            action={{ label: text.viewSnapshot, onOpen: props.onOpen, columnId: "number" }}
          />
        ) : (
          <Text role="status">{text.empty}</Text>
        ))}
      <fieldset className="app-pagination" aria-label={text.pages}>
        <AppButton disabled={loading || props.firstPage} onClick={props.onFirst}>
          {text.first}
        </AppButton>
        <AppButton disabled={loading || !props.state.page?.nextCursor} onClick={props.onNext}>
          {text.next}
        </AppButton>
      </fieldset>
    </section>
  );
}
