import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { leaveStatuses } from "../../../leave/domain/entities/leave-request";
import type { useLeaveReportFilters } from "../controllers/use-leave-report-filters";
import { reportingMessages } from "../i18n/reporting-messages";
import type { LeaveReportRequestsState } from "../models/leave-report-requests-state";
import type { leaveReportRequestsView } from "../models/leave-report-view";

export function LeaveReportRequestsPage({
  state,
  rows,
  filters,
  companyName,
  locale,
  firstPage,
  backTo,
  onRefresh,
  onFirst,
  onNext,
  onOpen,
}: {
  state: LeaveReportRequestsState;
  rows: ReturnType<typeof leaveReportRequestsView>;
  filters: ReturnType<typeof useLeaveReportFilters>;
  companyName: string;
  locale: Locale;
  firstPage: boolean;
  backTo: string;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const text = reportingMessages(locale);
  const shared = messages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.leaveRequestsTitle}
        context={`${companyName} / ${shared.reports}`}
        actions={
          <>
            <Link to={backTo}>{text.backToReport}</Link>
            <AppButton
              icon={<ArrowClockwise20Regular />}
              onClick={onRefresh}
              disabled={state.stage === "loading"}
            >
              {shared.refresh}
            </AppButton>
          </>
        }
      />
      <form onSubmit={filters.apply} noValidate>
        <AppCommandBar label={text.filters}>
          <AppTextField
            label={text.from}
            type="date"
            {...filters.from}
            min="1900-01-01"
            max="2200-12-31"
            error={filters.error}
          />
          <AppTextField
            label={text.until}
            type="date"
            {...filters.until}
            min="1900-01-01"
            max="2200-12-31"
          />
          <AppSelect label={text.leaveStatus} {...filters.status} error={filters.statusError}>
            <option value="">{text.allStatuses}</option>
            {leaveStatuses.map((status) => (
              <option key={status} value={status}>
                {text[status]}
              </option>
            ))}
          </AppSelect>
          <AppButton type="submit" appearance="primary">
            {text.apply}
          </AppButton>
        </AppCommandBar>
      </form>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={shared.loading} />}
      {state.page && (
        <div className="app-report-content">
          <Text className="app-muted">{text.leaveRowsDefinition}</Text>
          {rows.length === 0 ? (
            <Text role="status">{text.emptyLeaveReport}</Text>
          ) : (
            <AppResourceTable
              title={text.leaveRequests}
              columns={[
                { id: "employee", label: text.leaveEmployee },
                { id: "number", label: text.leaveNumber },
                { id: "type", label: text.leaveType },
                { id: "status", label: text.leaveStatus },
                { id: "days", label: text.requestedDays, numeric: true },
                { id: "requestDays", label: text.requestDays, numeric: true },
              ]}
              rows={rows}
              action={{ label: text.viewRequest, onOpen, columnId: "employee" }}
            />
          )}
        </div>
      )}
      <fieldset className="app-pagination" aria-label={text.reportPages}>
        <AppButton onClick={onFirst} disabled={firstPage || state.stage === "loading"}>
          {text.firstPage}
        </AppButton>
        <AppButton onClick={onNext} disabled={!state.page?.nextCursor || state.stage === "loading"}>
          {text.nextPage}
        </AppButton>
      </fieldset>
    </section>
  );
}
