import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppMetricCard } from "../../../../core/presentation/components/app-metric-card";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTabs } from "../../../../core/presentation/components/app-tabs";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { usePayrollPayment } from "../controllers/use-payroll-payment";
import { payrollPaymentMessages } from "../i18n/payroll-payment-messages";
import { payrollPaymentReconciliationMessages } from "../i18n/payroll-payment-reconciliation-messages";
import { type PayrollPaymentTab, payrollPaymentTabs } from "../models/payroll-payment-route";
import type { PayrollPaymentTransitionIntent } from "../models/payroll-payment-transition-intent";
import type { payrollPaymentTransitionActions } from "../models/payroll-payment-transition-view";
import type { payrollPaymentView } from "../models/payroll-payment-view";
export function PayrollPaymentPage(props: {
  state: ReturnType<typeof usePayrollPayment>;
  view: ReturnType<typeof payrollPaymentView> | null;
  transitions: ReturnType<typeof payrollPaymentTransitionActions>;
  canReconcile: boolean;
  onReconcile: () => void;
  onTransition: (intent: PayrollPaymentTransitionIntent) => void;
  locale: Locale;
  companyName: string;
  backTo: string;
  tab: PayrollPaymentTab;
  firstHistory: boolean;
  worksheet: ReactNode;
  onTab: (tab: string) => void;
  onItem: (id: string | null) => void;
  onChange: (version: string | null) => void;
  onFirstHistory: () => void;
  onNextHistory: () => void;
}) {
  const { state, view, locale } = props,
    text = payrollPaymentMessages(locale);
  return (
    <>
      <section aria-busy={state.loading}>
        <AppPageHeader
          title={view?.title ?? text.details}
          context={props.companyName}
          actions={
            <>
              {props.canReconcile && (
                <AppButton appearance="primary" onClick={props.onReconcile}>
                  {payrollPaymentReconciliationMessages(locale).action}
                </AppButton>
              )}
              {props.transitions.map((action) => (
                <AppButton
                  key={action.intent}
                  appearance={action.intent === "release" ? "primary" : "subtle"}
                  onClick={() => props.onTransition(action.intent)}
                >
                  {action.label}
                </AppButton>
              ))}
              <Link to={props.backTo}>{text.title}</Link>
              <AppButton
                appearance="subtle"
                icon={<ArrowClockwise20Regular />}
                disabled={state.loading}
                onClick={state.refresh}
              >
                {messages(locale).refresh}
              </AppButton>
            </>
          }
        />
        <AppFailure failure={state.failure} locale={locale} />
        {state.loading && <AppLoading label={messages(locale).loading} />}
        {view && (
          <>
            <AppTabs
              id="payroll-payment"
              label={text.details}
              tabs={payrollPaymentTabs.map((id) => ({ id, label: text[id] }))}
              selected={props.tab}
              onSelected={props.onTab}
            />
            <div
              id="payroll-payment-panel"
              role="tabpanel"
              aria-labelledby={`payroll-payment-tab-${props.tab}`}
              className="app-report-content"
            >
              {props.tab === "overview" && (
                <>
                  <div className="app-metric-grid">
                    {view.metrics.map((metric) => (
                      <AppMetricCard key={metric.label} {...metric} />
                    ))}
                  </div>
                  <AppPropertyList title={text.details} items={view.properties} />
                  <p className="app-muted">{view.note}</p>
                </>
              )}
              {props.tab === "instructions" && (
                <>
                  {props.worksheet}
                  <p className="app-muted">{text.worksheetNote}</p>
                  <AppResourceTable
                    title={text.instructions}
                    minWidth={900}
                    columns={[
                      { id: "employee", label: text.employee },
                      { id: "bank", label: text.bank },
                      { id: "account", label: text.accountNumber },
                      { id: "amount", label: text.amount, numeric: true },
                      { id: "status", label: text.status },
                    ]}
                    rows={view.instructions}
                    action={{ label: text.viewItem, onOpen: props.onItem }}
                  />
                </>
              )}
              {props.tab === "results" &&
                (view.results.length === 0 ? (
                  <Text role="status">{text.emptyResults}</Text>
                ) : (
                  <AppResourceTable
                    title={text.results}
                    minWidth={920}
                    columns={[
                      { id: "employee", label: text.employee },
                      { id: "amount", label: text.amount, numeric: true },
                      { id: "status", label: text.status },
                      { id: "reference", label: text.reference },
                      { id: "occurred", label: text.occurred },
                    ]}
                    rows={view.results}
                    action={{ label: text.viewItem, onOpen: props.onItem }}
                  />
                ))}
              {props.tab === "history" && (
                <>
                  <AppResourceTable
                    title={text.history}
                    minWidth={760}
                    columns={[
                      { id: "version", label: text.version, numeric: true },
                      { id: "action", label: text.action },
                      { id: "status", label: text.status },
                      { id: "recorded", label: text.recorded },
                    ]}
                    rows={view.history}
                    action={{ label: text.viewChange, onOpen: props.onChange }}
                  />
                  <fieldset className="app-pagination" aria-label={text.historyPages}>
                    <AppButton
                      appearance="subtle"
                      disabled={props.firstHistory || state.loading}
                      onClick={props.onFirstHistory}
                    >
                      {text.firstHistory}
                    </AppButton>
                    <AppButton
                      appearance="subtle"
                      disabled={state.review?.history.nextCursor == null || state.loading}
                      onClick={props.onNextHistory}
                    >
                      {text.nextHistory}
                    </AppButton>
                  </fieldset>
                </>
              )}
            </div>
          </>
        )}
      </section>
      <AppDetailsPanel
        open={view?.item != null}
        title={text.itemDetails}
        closeLabel={messages(locale).close}
        onClose={() => props.onItem(null)}
      >
        {view?.item && <AppPropertyList title={text.itemDetails} items={view.item} />}
      </AppDetailsPanel>
      <AppDetailsPanel
        open={view?.change != null}
        title={text.changeDetails}
        closeLabel={messages(locale).close}
        onClose={() => props.onChange(null)}
      >
        {view?.change && <AppPropertyList title={text.history} items={view.change} />}
      </AppDetailsPanel>
    </>
  );
}
