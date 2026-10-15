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
import { payrollAmendmentPublicationMessages } from "../i18n/payroll-amendment-publication-messages";
import type { PayrollAmendmentPublicationState } from "../models/payroll-amendment-publication-state";
import type { payrollAmendmentPublicationView } from "../models/payroll-amendment-publication-view";

export function PayrollAmendmentPublicationPage(props: {
  state: PayrollAmendmentPublicationState;
  view: ReturnType<typeof payrollAmendmentPublicationView> | null;
  locale: Locale;
  companyName: string;
  backTo: string | null;
  recoverTo: string | null;
  jobTo: string | null;
  proposalTo: string | null;
  calculationTo: string | null;
  reviewTo: string | null;
  onRefresh: () => void;
}) {
  const { state, view, locale } = props,
    text = payrollAmendmentPublicationMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.detail}
        context={props.companyName}
        actions={props.backTo && <Link to={props.backTo}>{text.back}</Link>}
      />
      <AppCommandBar label={text.detail}>
        <AppButton
          appearance="subtle"
          icon={<ArrowSync20Regular />}
          disabled={state.stage === "loading" || state.stage === "updating"}
          onClick={props.onRefresh}
        >
          {messages(locale).refresh}
        </AppButton>
        {props.recoverTo && <Link to={props.recoverTo}>{text.recovery}</Link>}
        {props.proposalTo && <Link to={props.proposalTo}>{text.proposal}</Link>}
        {props.calculationTo && <Link to={props.calculationTo}>{text.calculationLink}</Link>}
        {props.reviewTo && <Link to={props.reviewTo}>{text.reviewLink}</Link>}
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
                <MessageBarBody>{text.complete}</MessageBarBody>
              </MessageBar>
            )}
            {view.stopped && (
              <MessageBar intent="warning" role="status">
                <MessageBarBody>{text.stopped}</MessageBarBody>
              </MessageBar>
            )}
            {view.exhausted && (
              <MessageBar intent="warning" role="status">
                <MessageBarBody>{text.exhausted}</MessageBarBody>
              </MessageBar>
            )}
            <AppFailure failure={view.failure} locale={locale} />
            <AppPropertyList title={text.detail} items={view.properties} />
            <AppPropertyList title={text.basis} items={view.basis} />
            <AppPropertyList title={text.progress} items={view.progress} />
          </>
        )}
      </div>
    </section>
  );
}
