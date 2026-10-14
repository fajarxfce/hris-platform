import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { ArrowSync20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { payrollFinalizationMessages } from "../i18n/payroll-finalization-messages";
import type { PayrollFinalizationState } from "../models/payroll-finalization-state";
import type { payrollFinalizationView } from "../models/payroll-finalization-view";
export function PayrollFinalizationPage(props: {
  state: PayrollFinalizationState;
  view: ReturnType<typeof payrollFinalizationView> | null;
  locale: Locale;
  companyName: string;
  backTo: string | null;
  jobTo: string | null;
  onRefresh: () => void;
}) {
  const { state, view, locale } = props,
    text = payrollFinalizationMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.publication}
        context={props.companyName}
        actions={props.backTo && <Link to={props.backTo}>{text.overview}</Link>}
      />
      <AppCommandBar label={text.publication}>
        <AppButton
          appearance="subtle"
          icon={<ArrowSync20Regular />}
          disabled={state.stage === "loading" || state.stage === "updating"}
          onClick={props.onRefresh}
        >
          {messages(locale).refresh}
        </AppButton>
        {props.jobTo && <Link to={props.jobTo}>{text.job}</Link>}
      </AppCommandBar>
      <div className="app-report-content">
        <AppFailure failure={state.failure} locale={locale} />
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.updatesPaused && <p role="status">{text.paused}</p>}
        {view && (
          <>
            {view.published && (
              <MessageBar intent="success" role="status">
                <MessageBarBody>
                  {text.publicationComplete} {text.paymentNote}
                </MessageBarBody>
              </MessageBar>
            )}
            <AppFailure failure={view.jobFailure} locale={locale} />
            <AppPropertyList title={text.progress} items={view.properties} />
            <AppPropertyList title={text.totals} items={view.totals} />
          </>
        )}
      </div>
    </section>
  );
}
