import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useHolidayFilters } from "../controllers/use-holiday-filters";
import { holidayMessages } from "../i18n/holiday-messages";
import type { HolidayDirectoryState } from "../models/holiday-directory-state";
import type { holidayDirectoryView } from "../models/holiday-view";

export function HolidaysPage(props: {
  state: HolidayDirectoryState;
  rows: ReturnType<typeof holidayDirectoryView>;
  filters: ReturnType<typeof useHolidayFilters>;
  companyName: string;
  locale: Locale;
  createTo: string | null;
  onRefresh: () => void;
  onOpen: (id: string) => void;
}) {
  const { state, rows, filters, locale } = props;
  const text = holidayMessages(locale);
  const shared = messages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={props.companyName}
        actions={
          <>
            {props.createTo && <Link to={props.createTo}>{text.create}</Link>}
            <AppButton
              icon={<ArrowClockwise20Regular />}
              disabled={state.stage === "loading"}
              onClick={props.onRefresh}
            >
              {shared.refresh}
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
              min="0001-01-01"
              max="9999-12-31"
              {...filters.from}
            />
            <AppTextField
              label={text.until}
              hint={text.rangeHint}
              type="date"
              min="0001-01-01"
              max="9999-12-31"
              {...filters.until}
            />
            <AppButton type="submit" appearance="primary">
              {text.apply}
            </AppButton>
          </div>
          {filters.error && <p role="alert">{filters.error}</p>}
        </AppCommandBar>
      </form>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={shared.loading} />}
      <div className="app-report-content">
        {state.holidays &&
          (rows.length === 0 ? (
            <Text role="status">{text.empty}</Text>
          ) : (
            <AppResourceTable
              title={text.directory}
              columns={[
                { id: "date", label: text.date },
                { id: "name", label: text.name },
                { id: "status", label: text.status },
              ]}
              rows={rows}
              action={{ label: text.view, onOpen: props.onOpen }}
            />
          ))}
      </div>
    </section>
  );
}
