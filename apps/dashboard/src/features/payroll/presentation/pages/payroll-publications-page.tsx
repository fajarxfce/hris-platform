import { ArrowSync20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { payrollPublicationStatuses } from "../../domain/entities/payroll-publication-search";
import type { usePayrollPublicationFilter } from "../controllers/use-payroll-publication-filter";
import { payrollFinalizationMessages } from "../i18n/payroll-finalization-messages";
import type { PayrollPublicationsState } from "../models/payroll-finalization-state";
import type { payrollPublicationRows } from "../models/payroll-finalization-view";
export function PayrollPublicationsPage(props: {
  state: PayrollPublicationsState;
  filter: ReturnType<typeof usePayrollPublicationFilter>;
  rows: ReturnType<typeof payrollPublicationRows>;
  locale: Locale;
  companyName: string;
  firstPage: boolean;
  onOpen: (id: string) => void;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
}) {
  const { state, filter, locale } = props,
    text = payrollFinalizationMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader title={text.title} context={props.companyName} />
      <AppCommandBar label={text.title}>
        <AppButton
          appearance="subtle"
          icon={<ArrowSync20Regular />}
          onClick={props.onRefresh}
          disabled={state.stage === "loading"}
        >
          {messages(locale).refresh}
        </AppButton>
      </AppCommandBar>
      <AppFailure failure={state.failure} locale={locale} />
      <div className="app-report-content">
        <form className="app-resource-filters" onSubmit={filter.submit} aria-label={text.filters}>
          <AppTextField
            label={text.from}
            type="month"
            min="2024-01"
            max="2100-12"
            {...filter.from}
          />
          <AppTextField
            label={text.until}
            type="month"
            min="2024-01"
            max="2100-12"
            {...filter.until}
          />
          <AppSelect label={text.status} {...filter.status}>
            <option value="">{text.all}</option>
            {payrollPublicationStatuses.map((status) => (
              <option key={status} value={status}>
                {text.runStatuses[status]}
              </option>
            ))}
          </AppSelect>
          <AppButton type="submit">{text.apply}</AppButton>
        </form>
        <p className="app-muted">{text.range}</p>
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.stage === "ready" && state.page && (
          <>
            {props.rows.length ? (
              <AppResourceTable
                title={text.title}
                columns={[
                  { id: "month", label: text.month },
                  { id: "reference", label: text.reference },
                  { id: "employees", label: text.employees, numeric: true },
                  { id: "status", label: text.status },
                ]}
                rows={props.rows}
                action={{ label: text.open, onOpen: props.onOpen }}
              />
            ) : (
              <p role="status">{text.empty}</p>
            )}
            <fieldset className="app-pagination" aria-label={text.pages}>
              <AppButton onClick={props.onFirst} disabled={props.firstPage}>
                {text.first}
              </AppButton>
              <AppButton onClick={props.onNext} disabled={state.page.nextCursor === null}>
                {text.next}
              </AppButton>
            </fieldset>
          </>
        )}
      </div>
    </section>
  );
}
