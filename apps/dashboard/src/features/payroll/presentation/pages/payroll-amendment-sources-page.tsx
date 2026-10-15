import { ArrowSync20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { usePayrollAmendmentFilter } from "../controllers/use-payroll-amendment-filter";
import { payrollAmendmentEditorMessages } from "../i18n/payroll-amendment-editor-messages";
import { payrollAmendmentMessages } from "../i18n/payroll-amendment-messages";
import type { PayrollAmendmentSourcesState } from "../models/payroll-amendment-editor-state";
import type { payrollAmendmentCandidateRows } from "../models/payroll-amendment-editor-view";
export function PayrollAmendmentSourcesPage(props: {
  state: PayrollAmendmentSourcesState;
  locale: Locale;
  companyName: string;
  backTo: string;
  filter: ReturnType<typeof usePayrollAmendmentFilter>;
  rows: ReturnType<typeof payrollAmendmentCandidateRows>;
  first: boolean;
  onOpen: (id: string) => void;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
}) {
  const { state, locale, filter } = props,
    text = payrollAmendmentEditorMessages(locale),
    read = payrollAmendmentMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.sources}
        context={`${props.companyName} / ${read.title}`}
        actions={<Link to={props.backTo}>{read.back}</Link>}
      />
      <AppCommandBar label={text.sources}>
        <AppButton
          appearance="subtle"
          icon={<ArrowSync20Regular />}
          disabled={state.stage === "loading"}
          onClick={props.onRefresh}
        >
          {messages(locale).refresh}
        </AppButton>
      </AppCommandBar>
      <AppFailure failure={state.failure} locale={locale} />
      <div className="app-report-content">
        <p>{text.sourceHint}</p>
        <form className="app-resource-filters" onSubmit={filter.submit} aria-label={read.filters}>
          <AppTextField
            label={read.year}
            type="number"
            min={2024}
            max={2100}
            step={1}
            required
            {...filter.year}
          />
          <AppTextField label={read.query} maxLength={120} {...filter.query} />
          <AppButton type="submit">{read.apply}</AppButton>
        </form>
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.stage === "ready" && state.data && (
          <>
            {props.rows.length ? (
              <AppResourceTable
                title={text.basis}
                minWidth={840}
                columns={[
                  { id: "employee", label: read.employee },
                  { id: "number", label: read.employeeNumber },
                  { id: "month", label: text.month },
                  { id: "revision", label: read.sourceRevision, numeric: true },
                  { id: "published", label: text.published },
                ]}
                rows={props.rows}
                action={{ label: text.select, onOpen: props.onOpen, columnId: "employee" }}
              />
            ) : (
              <p role="status">{text.empty}</p>
            )}
            <fieldset className="app-pagination" aria-label={read.pages}>
              <AppButton onClick={props.onFirst} disabled={props.first}>
                {read.first}
              </AppButton>
              <AppButton onClick={props.onNext} disabled={state.data.nextCursor === null}>
                {read.next}
              </AppButton>
            </fieldset>
          </>
        )}
      </div>
    </section>
  );
}
