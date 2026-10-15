import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { ArrowDownload20Regular, ArrowSync20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppMetricCard } from "../../../../core/presentation/components/app-metric-card";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTabs } from "../../../../core/presentation/components/app-tabs";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { payrollPayslipMessages } from "../i18n/payroll-payslip-messages";
import type { PayrollPayslipState } from "../models/payroll-payslip-state";
import type {
  payrollPayslipPaymentRows,
  payrollPayslipSettlementView,
  payrollPayslipView,
} from "../models/payroll-payslip-view";
import { PayrollCalculationOutputSection } from "./payroll-calculation-output-section";

export function PayrollPayslipPage(props: {
  state: PayrollPayslipState;
  view: ReturnType<typeof payrollPayslipView> | null;
  paymentRows: ReturnType<typeof payrollPayslipPaymentRows>;
  settlement: ReturnType<typeof payrollPayslipSettlementView>;
  locale: Locale;
  companyName: string;
  timezone: string;
  backTo: string;
  tab: string;
  onRefresh: () => void;
  onRefreshPayments: () => void;
  onDownload: () => void;
  onLanguage: (language: string) => void;
  onTab: (tab: string) => void;
}) {
  const { state, view, locale } = props,
    text = payrollPayslipMessages(locale);
  return (
    <section className="app-payroll-payslip" aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={view?.title ?? text.detail}
        context={props.companyName}
        actions={<Link to={props.backTo}>{text.back}</Link>}
      />
      <AppCommandBar label={text.detail}>
        <AppButton
          appearance="subtle"
          icon={<ArrowSync20Regular />}
          onClick={props.onRefresh}
          disabled={state.stage === "loading" || state.download === "saving"}
        >
          {messages(locale).refresh}
        </AppButton>
      </AppCommandBar>
      <div className="app-report-content">
        <AppFailure failure={state.failure} locale={locale} />
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {view && state.payslip && (
          <>
            <div className="app-metric-grid app-payroll-payslip-metrics">
              {view.metrics.map((metric) => (
                <AppMetricCard key={metric.label} {...metric} />
              ))}
            </div>
            <AppPropertyList title={text.summary} items={view.overview} />
            <div className="app-resource-filters">
              <AppSelect
                label={text.language}
                value={state.documentLanguage}
                disabled={state.download === "saving"}
                onChange={(_, data) => props.onLanguage(data.value)}
              >
                <option value="ID">Bahasa Indonesia</option>
                <option value="EN">English</option>
              </AppSelect>
              <AppButton
                icon={<ArrowDownload20Regular />}
                onClick={props.onDownload}
                disabled={state.download === "saving"}
              >
                {state.download === "saving" ? text.downloading : text.download}
              </AppButton>
            </div>
            <AppFailure failure={state.downloadFailure} locale={locale} />
            {state.download === "saved" && (
              <MessageBar intent="success" role="status">
                <MessageBarBody>{text.downloaded}</MessageBarBody>
              </MessageBar>
            )}
            <AppTabs
              id="payroll-payslip"
              label={text.detail}
              tabs={[
                { id: "calculation", label: text.output },
                { id: "payments", label: text.payments },
              ]}
              selected={props.tab}
              onSelected={props.onTab}
            />
            <div
              className="app-report-content"
              role="tabpanel"
              id="payroll-payslip-panel"
              aria-labelledby={`payroll-payslip-tab-${props.tab}`}
            >
              {props.tab === "calculation" && (
                <PayrollCalculationOutputSection view={view.output} locale={locale} />
              )}
              {props.tab === "payments" && (
                <>
                  <AppCommandBar label={text.payments}>
                    <AppButton
                      appearance="subtle"
                      icon={<ArrowSync20Regular />}
                      onClick={props.onRefreshPayments}
                      disabled={state.paymentsLoading}
                    >
                      {text.refreshPayments}
                    </AppButton>
                  </AppCommandBar>
                  <p className="app-muted">{text.paymentNote}</p>
                  <AppFailure failure={state.paymentFailure} locale={locale} />
                  {state.paymentsLoading && <AppLoading label={messages(locale).loading} />}
                  {!state.paymentsLoading && props.settlement && (
                    <AppPropertyList title={text.settlement} items={props.settlement} />
                  )}
                  {!state.paymentsLoading &&
                    state.payments &&
                    (props.paymentRows.length ? (
                      <AppResourceTable
                        title={`${text.payments} (${props.timezone})`}
                        columns={[
                          { id: "attempt", label: text.attempt },
                          { id: "status", label: text.status },
                          { id: "amount", label: text.amount },
                          { id: "revision", label: text.revision },
                          { id: "created", label: text.created },
                          { id: "released", label: text.released },
                          { id: "resolved", label: text.resolved },
                        ]}
                        rows={props.paymentRows}
                      />
                    ) : (
                      <p role="status">{text.noPayments}</p>
                    ))}
                </>
              )}
            </div>
          </>
        )}
      </div>
    </section>
  );
}
