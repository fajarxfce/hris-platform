import { ArrowSync20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTabs } from "../../../../core/presentation/components/app-tabs";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { payrollCalculationMessages } from "../i18n/payroll-calculation-messages";
import type { PayrollRunEmployeeState } from "../models/payroll-calculation-state";
import type { payrollRunEmployeeView } from "../models/payroll-run-employee-view";
import { PayrollCalculationOutputSection } from "./payroll-calculation-output-section";
export function PayrollRunEmployeePage(props: {
  state: PayrollRunEmployeeState;
  view: ReturnType<typeof payrollRunEmployeeView> | null;
  locale: Locale;
  companyName: string;
  backTo: string;
  tab: string;
  onRefresh: () => void;
  onTab: (tab: string) => void;
}) {
  const { state, view, locale } = props,
    text = payrollCalculationMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={view ? `${text.result} · ${view.title}` : text.result}
        context={props.companyName}
        actions={<Link to={props.backTo}>{text.openRun}</Link>}
      />
      <AppCommandBar label={text.result}>
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
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {view && state.employee && (
          <>
            <AppPropertyList title={text.summary} items={view.overview} />
            <AppFailure failure={state.employee.item.failure} locale={locale} />
            <AppTabs
              id="payroll-employee-result"
              label={text.result}
              tabs={[
                { id: "calculation", label: text.output },
                { id: "sources", label: text.sources },
              ]}
              selected={props.tab}
              onSelected={props.onTab}
            />
            <div
              className="app-report-content"
              role="tabpanel"
              id="payroll-employee-result-panel"
              aria-labelledby={`payroll-employee-result-tab-${props.tab}`}
            >
              {props.tab === "calculation" && (
                <PayrollCalculationOutputSection view={view} locale={locale} />
              )}
              {props.tab === "sources" && (
                <>
                  <AppPropertyList title={text.sources} items={view.references} />
                  {view.source ? (
                    <AppPropertyList title={text.review} items={view.source} />
                  ) : (
                    <p>{text.missingEvidence}</p>
                  )}
                  {view.work.length > 0 && (
                    <AppResourceTable
                      title={text.attendance}
                      columns={[
                        { id: "date", label: text.date },
                        { id: "schedule", label: text.schedule },
                        { id: "attendance", label: text.status },
                        { id: "holiday", label: text.officialHoliday },
                      ]}
                      rows={view.work}
                    />
                  )}
                  {view.leave.length > 0 && (
                    <AppResourceTable
                      title={text.leave}
                      columns={[
                        { id: "date", label: text.date },
                        { id: "portion", label: text.portion },
                        { id: "paid", label: text.paid },
                        { id: "id", label: text.reference },
                      ]}
                      rows={view.leave}
                    />
                  )}
                </>
              )}
            </div>
          </>
        )}
      </div>
    </section>
  );
}
