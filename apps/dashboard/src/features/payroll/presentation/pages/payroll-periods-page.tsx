import { Add20Regular, ArrowSync20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { payrollPeriodStatuses } from "../../domain/entities/payroll-period";
import type { usePayrollPeriodFilter } from "../controllers/use-payroll-period-filter";
import { payrollPeriodMessages } from "../i18n/payroll-period-messages";
import type { PayrollPeriodsState } from "../models/payroll-period-state";
import type { payrollPeriodRows } from "../models/payroll-period-view";
export function PayrollPeriodsPage(props: {
  state: PayrollPeriodsState;
  filter: ReturnType<typeof usePayrollPeriodFilter>;
  rows: ReturnType<typeof payrollPeriodRows>;
  locale: Locale;
  companyName: string;
  canCreate: boolean;
  firstPage: boolean;
  onCreate: () => void;
  onOpen: (id: string) => void;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
}) {
  const { state, filter, locale } = props,
    text = payrollPeriodMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader title={text.title} context={props.companyName} />
      <AppCommandBar label={text.title}>
        {props.canCreate && (
          <AppButton appearance="subtle" icon={<Add20Regular />} onClick={props.onCreate}>
            {text.create}
          </AppButton>
        )}
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
            {payrollPeriodStatuses.map((status) => (
              <option key={status} value={status}>
                {text[status]}
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
                  { id: "month", label: text.earningsMonth },
                  { id: "date", label: text.paymentDate },
                  { id: "employees", label: text.participants, numeric: true },
                  { id: "status", label: text.status },
                  { id: "version", label: text.version, numeric: true },
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
