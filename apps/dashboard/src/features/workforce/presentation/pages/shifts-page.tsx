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
import type { useShiftFilters } from "../controllers/use-shift-filters";
import { shiftMessages } from "../i18n/shift-messages";
import type { ShiftDirectoryState } from "../models/shift-directory-state";
import type { shiftDirectoryView } from "../models/shift-view";

export function ShiftsPage(props: {
  state: ShiftDirectoryState;
  rows: ReturnType<typeof shiftDirectoryView>;
  filters: ReturnType<typeof useShiftFilters>;
  companyName: string;
  locale: Locale;
  firstPage: boolean;
  createTo: string | null;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const { state, rows, filters, locale } = props;
  const text = shiftMessages(locale);
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
            <AppTextField label={text.query} maxLength={120} {...filters.query} />
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
                { id: "code", label: text.code },
                { id: "name", label: text.name },
                { id: "hours", label: text.hours },
                { id: "mode", label: text.mode },
                { id: "status", label: text.status },
              ]}
              rows={rows}
              action={{ label: text.view, onOpen: props.onOpen }}
            />
          ))}
        <fieldset className="app-pagination" aria-label={text.page}>
          <AppButton
            onClick={props.onFirst}
            disabled={props.firstPage || state.stage === "loading"}
          >
            {text.first}
          </AppButton>
          <AppButton
            onClick={props.onNext}
            disabled={state.page?.nextCursor == null || state.stage === "loading"}
          >
            {text.next}
          </AppButton>
        </fieldset>
      </div>
    </section>
  );
}
