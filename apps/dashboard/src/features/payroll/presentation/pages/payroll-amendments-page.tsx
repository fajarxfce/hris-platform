import { ArrowSync20Regular } from "@fluentui/react-icons";
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
import { payrollAmendmentStatuses } from "../../domain/entities/payroll-amendment";
import type { usePayrollAmendmentFilter } from "../controllers/use-payroll-amendment-filter";
import { payrollAmendmentEditorMessages } from "../i18n/payroll-amendment-editor-messages";
import { payrollAmendmentMessages } from "../i18n/payroll-amendment-messages";
import type { PayrollAmendmentsState } from "../models/payroll-amendment-state";
import type { payrollAmendmentRows } from "../models/payroll-amendment-view";
export function PayrollAmendmentsPage(props: {
  state: PayrollAmendmentsState;
  filter: ReturnType<typeof usePayrollAmendmentFilter>;
  rows: ReturnType<typeof payrollAmendmentRows>;
  locale: Locale;
  companyName: string;
  createTo: string | null;
  first: boolean;
  onOpen: (id: string) => void;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
}) {
  const { state, filter, locale } = props,
    text = payrollAmendmentMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader title={text.title} context={props.companyName} />
      <AppCommandBar label={text.title}>
        {props.createTo && (
          <Link to={props.createTo}>{payrollAmendmentEditorMessages(locale).create}</Link>
        )}
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
        <form className="app-resource-filters" onSubmit={filter.submit} aria-label={text.filters}>
          <AppTextField
            label={text.year}
            type="number"
            min={2024}
            max={2100}
            step={1}
            required
            {...filter.year}
          />
          <AppTextField label={text.query} maxLength={120} {...filter.query} />
          <AppSelect label={text.status} {...filter.status}>
            <option value="">{text.all}</option>
            {payrollAmendmentStatuses.map((status) => (
              <option key={status} value={status}>
                {text.statuses[status]}
              </option>
            ))}
          </AppSelect>
          <AppButton type="submit">{text.apply}</AppButton>
        </form>
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.stage === "ready" && state.data && (
          <>
            {props.rows.length ? (
              <AppResourceTable
                title={text.title}
                minWidth={1040}
                columns={[
                  { id: "employee", label: text.employee },
                  { id: "number", label: text.employeeNumber },
                  { id: "month", label: text.firstMonth },
                  { id: "status", label: text.status },
                  { id: "revision", label: text.revision, numeric: true },
                  { id: "recorded", label: text.recorded },
                ]}
                rows={props.rows}
                action={{ label: text.open, onOpen: props.onOpen }}
              />
            ) : (
              <p role="status">{text.empty}</p>
            )}
            <fieldset className="app-pagination" aria-label={text.pages}>
              <AppButton onClick={props.onFirst} disabled={props.first}>
                {text.first}
              </AppButton>
              <AppButton onClick={props.onNext} disabled={state.data.nextCursor === null}>
                {text.next}
              </AppButton>
            </fieldset>
          </>
        )}
      </div>
    </section>
  );
}
