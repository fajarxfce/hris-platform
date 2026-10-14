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
import type { usePayrollPayslipFilter } from "../controllers/use-payroll-payslip-filter";
import { payrollPayslipMessages } from "../i18n/payroll-payslip-messages";
import type { PayrollPayslipsState } from "../models/payroll-payslip-state";
import type { payrollPayslipRows } from "../models/payroll-payslip-view";

export function PayrollPayslipsPage(props: {
  state: PayrollPayslipsState;
  filter: ReturnType<typeof usePayrollPayslipFilter>;
  rows: ReturnType<typeof payrollPayslipRows>;
  locale: Locale;
  companyName: string;
  companyReader: boolean;
  firstPage: boolean;
  onOpen: (id: string) => void;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
}) {
  const { state, filter, locale } = props,
    text = payrollPayslipMessages(locale);
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
      <div className="app-report-content">
        <AppFailure failure={state.failure} locale={locale} />
        <form className="app-resource-filters" onSubmit={filter.submit} aria-label={text.filters}>
          <AppTextField label={text.query} type="search" maxLength={120} {...filter.query} />
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
          <AppSelect label={text.scope} {...filter.scope} disabled={!props.companyReader}>
            {props.companyReader && <option value="AUTHORIZED">{text.company}</option>}
            <option value="SELF">{text.self}</option>
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
                  { id: "name", label: text.employee },
                  { id: "number", label: text.number },
                  { id: "month", label: text.month },
                  { id: "gross", label: text.gross, numeric: true },
                  { id: "withheld", label: text.withheld, numeric: true },
                  { id: "net", label: text.takeHome, numeric: true },
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
