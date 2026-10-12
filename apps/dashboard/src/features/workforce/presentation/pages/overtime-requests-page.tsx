import { Text } from "@fluentui/react-components";
import { Add20Regular, ArrowClockwise20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { overtimeStatuses } from "../../domain/entities/overtime-request";
import type { useOvertimeFilters } from "../controllers/use-overtime-filters";
import type { useOvertimeRequests } from "../controllers/use-overtime-requests";
import { overtimeMessages } from "../i18n/overtime-messages";
import { overtimePlanningMessages } from "../i18n/overtime-planning-messages";
import type { overtimeRows } from "../models/overtime-view";

export function OvertimeRequestsPage(props: {
  state: ReturnType<typeof useOvertimeRequests>;
  filters: ReturnType<typeof useOvertimeFilters>;
  rows: ReturnType<typeof overtimeRows>;
  locale: Locale;
  companyName: string;
  firstPage: boolean;
  canCreate: boolean;
  onCreate: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const { state, filters, locale } = props,
    text = overtimeMessages(locale);
  return (
    <section aria-busy={state.loading}>
      <AppPageHeader
        title={text.title}
        context={props.companyName}
        actions={
          <>
            {props.canCreate && (
              <AppButton appearance="primary" icon={<Add20Regular />} onClick={props.onCreate}>
                {overtimePlanningMessages(locale).title}
              </AppButton>
            )}
            <AppButton
              icon={<ArrowClockwise20Regular />}
              appearance="subtle"
              disabled={state.loading}
              onClick={state.refresh}
            >
              {messages(locale).refresh}
            </AppButton>
          </>
        }
      />
      <form onSubmit={filters.apply} noValidate>
        <AppCommandBar label={text.filters}>
          <div className="app-form-actions">
            <AppTextField
              label={text.from}
              type="date"
              min="2000-01-01"
              max="2100-12-31"
              {...filters.from}
            />
            <AppTextField
              label={text.until}
              type="date"
              min="2000-01-01"
              max="2100-12-31"
              {...filters.until}
            />
            <AppSelect label={text.status} {...filters.status}>
              <option value="">{text.all}</option>
              {overtimeStatuses.map((status) => (
                <option key={status} value={status}>
                  {text[status]}
                </option>
              ))}
            </AppSelect>
            <AppButton type="submit" appearance="primary">
              {text.apply}
            </AppButton>
          </div>
          <Text>{text.range}</Text>
          {filters.error && <p role="alert">{filters.error}</p>}
        </AppCommandBar>
      </form>
      <AppFailure failure={state.failure} locale={locale} />
      {state.loading && <AppLoading label={messages(locale).loading} />}
      <div className="app-report-content">
        {state.page &&
          (props.rows.length === 0 ? (
            <Text role="status">{text.empty}</Text>
          ) : (
            <AppResourceTable
              title={text.directory}
              columns={[
                { id: "date", label: text.date },
                { id: "employee", label: text.employee },
                { id: "status", label: text.status },
                { id: "requested", label: text.requested, numeric: true },
                { id: "approved", label: text.approved, numeric: true },
              ]}
              rows={props.rows}
              action={{ label: text.view, onOpen: props.onOpen }}
            />
          ))}
        <fieldset className="app-pagination" aria-label={text.pages}>
          <AppButton
            appearance="subtle"
            disabled={props.firstPage || state.loading}
            onClick={props.onFirst}
          >
            {text.first}
          </AppButton>
          <AppButton
            appearance="subtle"
            disabled={state.page?.nextCursor == null || state.loading}
            onClick={props.onNext}
          >
            {text.next}
          </AppButton>
        </fieldset>
      </div>
    </section>
  );
}
