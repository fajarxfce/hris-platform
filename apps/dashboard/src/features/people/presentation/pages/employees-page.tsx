import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useEmployeeFilters } from "../controllers/use-employee-filters";
import { peopleMessages } from "../i18n/people-messages";
import type { EmployeeDirectoryState } from "../models/employee-directory-state";
import type { employeeDirectoryView } from "../models/employee-view";

export function EmployeesPage({
  state,
  rows,
  filters,
  companyName,
  locale,
  firstPage,
  onRefresh,
  onFirst,
  onNext,
  onOpen,
}: {
  state: EmployeeDirectoryState;
  rows: ReturnType<typeof employeeDirectoryView>;
  filters: ReturnType<typeof useEmployeeFilters>;
  companyName: string;
  locale: Locale;
  firstPage: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const text = peopleMessages(locale);
  const shared = messages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={companyName}
        actions={
          <AppButton
            icon={<ArrowClockwise20Regular />}
            disabled={state.stage === "loading"}
            onClick={onRefresh}
          >
            {shared.refresh}
          </AppButton>
        }
      />
      <form onSubmit={filters.apply} noValidate>
        <AppCommandBar label={text.filters}>
          <div className="app-directory-filters">
            <AppTextField label={text.query} maxLength={120} {...filters.query} />
            <AppTextField
              label={text.asOf}
              type="date"
              min="0001-01-01"
              max="9999-12-31"
              {...filters.date}
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
        {state.page &&
          (rows.length === 0 ? (
            <Text role="status">{text.empty}</Text>
          ) : (
            <AppResourceTable
              title={text.directory}
              columns={[
                { id: "number", label: text.number },
                { id: "name", label: text.name },
                { id: "status", label: text.status },
                { id: "contract", label: text.contract },
              ]}
              rows={rows}
              action={{ label: text.view, onOpen }}
            />
          ))}
        <fieldset className="app-pagination" aria-label={text.page}>
          <AppButton onClick={onFirst} disabled={firstPage || state.stage === "loading"}>
            {text.first}
          </AppButton>
          <AppButton
            onClick={onNext}
            disabled={state.page?.nextCursor == null || state.stage === "loading"}
          >
            {text.next}
          </AppButton>
        </fieldset>
      </div>
    </section>
  );
}
