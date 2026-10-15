import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { Add20Regular, ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { usePayrollPaymentRecoveryForm } from "../controllers/use-payroll-payment-recovery-form";
import { payrollPaymentRecoveryMessages } from "../i18n/payroll-payment-recovery-messages";
import type { payrollPaymentRecoveryRoute } from "../models/payroll-payment-recovery-route";
import type { PayrollPaymentRecoveryState } from "../models/payroll-payment-recovery-state";
import type { payrollPaymentRecoveryView } from "../models/payroll-payment-recovery-view";
import { PayrollPaymentRecoveryForm } from "./payroll-payment-recovery-form";

export function PayrollPaymentRecoveryPage(props: {
  state: PayrollPaymentRecoveryState;
  view: ReturnType<typeof payrollPaymentRecoveryView> | null;
  form: ReturnType<typeof usePayrollPaymentRecoveryForm>;
  route: ReturnType<typeof payrollPaymentRecoveryRoute>;
  record: boolean;
  locale: Locale;
  timezone: string;
  companyName: string;
  onRecord: () => void;
  onRetry: () => void;
  onSelected: (id: string | null) => void;
}) {
  const { state, view, form, route, record, locale } = props,
    text = payrollPaymentRecoveryMessages(locale);
  return (
    <>
      <section aria-busy={state.stage === "loading" || state.stage === "submitting"}>
        <AppPageHeader
          title={record ? text.record : text.title}
          context={props.companyName}
          actions={
            <Link to={record ? route.review : route.backTo}>
              {record ? text.title : text[route.backLabel]}
            </Link>
          }
        />
        <AppCommandBar label={text.title}>
          {!record && state.stage === "ready" && state.review?.canConfirm && (
            <AppButton appearance="subtle" icon={<Add20Regular />} onClick={props.onRecord}>
              {text.record}
            </AppButton>
          )}
          {state.stage !== "saved" && (
            <AppButton
              appearance="subtle"
              icon={<ArrowClockwise20Regular />}
              onClick={form.refresh}
              disabled={
                state.stage === "loading" ||
                state.stage === "submitting" ||
                state.stage === "unconfirmed"
              }
            >
              {record ? text.refresh : messages(locale).refresh}
            </AppButton>
          )}
        </AppCommandBar>
        <div className="app-report-content">
          <AppFailure failure={state.failure} locale={locale} />
          {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
          {state.stage === "submitting" && <AppLoading label={text.saving} />}
          {state.stage === "conflict" && (
            <MessageBar intent="warning" role="status">
              <MessageBarBody>{text.conflict}</MessageBarBody>
            </MessageBar>
          )}
          {state.stage === "unconfirmed" && (
            <MessageBar intent="warning" layout="multiline" role="status">
              <MessageBarBody>
                {text.unconfirmed} <AppButton onClick={props.onRetry}>{text.retry}</AppButton>
              </MessageBarBody>
            </MessageBar>
          )}
          {state.stage === "saved" && (
            <MessageBar intent="success" role="status">
              <MessageBarBody>
                {text.saved} <Link to={route.review}>{text.review}</Link>
              </MessageBarBody>
            </MessageBar>
          )}
          {view && (
            <>
              <div className="app-payroll-return-overview">
                <AppPropertyList title={view.employee} items={view.properties} />
                <AppPropertyList title={text.balance} items={view.settlement} />
              </div>
              {view.blockers.map((blocker) => (
                <MessageBar key={blocker.id} intent="info">
                  <MessageBarBody>{blocker.text}</MessageBarBody>
                </MessageBar>
              ))}
              {record ? (
                state.review?.canConfirm && (
                  <PayrollPaymentRecoveryForm
                    form={form}
                    locale={locale}
                    timezone={props.timezone}
                  />
                )
              ) : view.rows.length === 0 ? (
                <p role="status">{text.noReturns}</p>
              ) : (
                <AppResourceTable
                  title={`${text.history} (${props.timezone})`}
                  minWidth={860}
                  columns={[
                    { id: "reference", label: text.reference },
                    { id: "amount", label: text.amountColumn, numeric: true },
                    { id: "revision", label: text.revision, numeric: true },
                    { id: "occurred", label: text.occurred },
                    { id: "recorded", label: text.recorded },
                  ]}
                  rows={view.rows}
                  action={{ label: text.view, onOpen: props.onSelected }}
                />
              )}
            </>
          )}
        </div>
      </section>
      <AppDetailsPanel
        title={text.details}
        open={!record && view?.selected != null}
        closeLabel={messages(locale).close}
        onClose={() => props.onSelected(null)}
      >
        {view?.selected && <AppPropertyList title={text.details} items={view.selected} />}
      </AppDetailsPanel>
    </>
  );
}
