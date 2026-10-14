import { ArrowSync20Regular, Dismiss20Regular } from "@fluentui/react-icons";
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
import { payrollPeriodMessages } from "../i18n/payroll-period-messages";
import type { PayrollPeriodState } from "../models/payroll-period-state";
import type { payrollPeriodDetailsView } from "../models/payroll-period-view";
export function PayrollPeriodPage(props: {
  state: PayrollPeriodState;
  view: ReturnType<typeof payrollPeriodDetailsView> | null;
  locale: Locale;
  companyName: string;
  backTo: string;
  tab: string;
  canCancel: boolean;
  calculationTo: string | null;
  firstPage: boolean;
  hasNext: boolean;
  onRefresh: () => void;
  onCancel: () => void;
  onTab: (tab: string) => void;
  onFirst: () => void;
  onNext: () => void;
}) {
  const { state, view, locale } = props,
    text = payrollPeriodMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={
          state.details ? `${text.details} · ${state.details.period.earningsMonth}` : text.details
        }
        context={props.companyName}
        actions={<Link to={props.backTo}>{text.back}</Link>}
      />
      <AppCommandBar label={text.details}>
        <AppButton
          appearance="subtle"
          icon={<ArrowSync20Regular />}
          onClick={props.onRefresh}
          disabled={state.stage === "loading"}
        >
          {messages(locale).refresh}
        </AppButton>
        {props.calculationTo && (
          <Link to={props.calculationTo}>{payrollCalculationMessages(locale).calculations}</Link>
        )}
        {props.canCancel && (
          <AppButton appearance="subtle" icon={<Dismiss20Regular />} onClick={props.onCancel}>
            {text.cancel}
          </AppButton>
        )}
      </AppCommandBar>
      <AppFailure failure={state.failure} locale={locale} />
      <div className="app-report-content">
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {view && (
          <>
            <AppPropertyList title={text.details} items={view.properties} />
            <p className="app-muted">{text.planNote}</p>
            <AppTabs
              id="payroll-period"
              label={text.details}
              tabs={[
                { id: "participants", label: text.participants },
                { id: "history", label: text.history },
              ]}
              selected={props.tab}
              onSelected={props.onTab}
            />
            <div
              role="tabpanel"
              id="payroll-period-panel"
              aria-labelledby={`payroll-period-tab-${props.tab}`}
            >
              {props.tab === "participants" ? (
                <AppResourceTable
                  title={text.participants}
                  columns={[
                    { id: "number", label: text.employeeNumber },
                    { id: "name", label: text.name },
                    { id: "input", label: text.inputStatus },
                    { id: "version", label: text.inputVersion, numeric: true },
                  ]}
                  rows={view.members}
                />
              ) : (
                <AppResourceTable
                  title={text.history}
                  columns={[
                    { id: "version", label: text.version, numeric: true },
                    { id: "status", label: text.status },
                    { id: "time", label: text.recorded },
                    { id: "reason", label: text.reason },
                    { id: "actor", label: text.actor },
                  ]}
                  rows={view.history}
                />
              )}
              <fieldset className="app-pagination" aria-label={text.pages}>
                <AppButton onClick={props.onFirst} disabled={props.firstPage}>
                  {text.first}
                </AppButton>
                <AppButton onClick={props.onNext} disabled={!props.hasNext}>
                  {text.next}
                </AppButton>
              </fieldset>
            </div>
          </>
        )}
      </div>
    </section>
  );
}
