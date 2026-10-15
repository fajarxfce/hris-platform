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
import { payrollAmendmentActionMessages } from "../i18n/payroll-amendment-action-messages";
import { payrollAmendmentEditorMessages } from "../i18n/payroll-amendment-editor-messages";
import { payrollAmendmentMessages } from "../i18n/payroll-amendment-messages";
import type { PayrollAmendmentState } from "../models/payroll-amendment-state";
import type { payrollAmendmentInspectionView } from "../models/payroll-amendment-view";
import { PayrollAmendmentCalculationSection } from "./payroll-amendment-calculation-section";
import { PayrollAmendmentProposalSection } from "./payroll-amendment-proposal-section";
export function PayrollAmendmentPage(props: {
  state: PayrollAmendmentState;
  view: ReturnType<typeof payrollAmendmentInspectionView> | null;
  locale: Locale;
  companyName: string;
  tab: string;
  backTo: string;
  latestTo: string | null;
  basisTo: string | null;
  editTo: string | null;
  calculateTo: string | null;
  cancelTo: string | null;
  first: boolean;
  onRefresh: () => void;
  onTab: (tab: string) => void;
  onRevision: (revision: string) => void;
  onMonth: (id: string) => void;
  onFirst: () => void;
  onNext: () => void;
}) {
  const { state, view, locale } = props,
    text = payrollAmendmentMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={view?.title ?? text.details}
        context={`${props.companyName} / ${text.title}`}
        actions={<Link to={props.backTo}>{text.back}</Link>}
      />
      <AppCommandBar label={text.details}>
        {props.editTo && (
          <Link to={props.editTo}>{payrollAmendmentEditorMessages(locale).edit}</Link>
        )}
        {props.calculateTo && (
          <Link to={props.calculateTo}>{payrollAmendmentActionMessages(locale).CALCULATE}</Link>
        )}
        {props.cancelTo && (
          <Link to={props.cancelTo}>{payrollAmendmentActionMessages(locale).CANCEL}</Link>
        )}
        {props.latestTo && <Link to={props.latestTo}>{text.latest}</Link>}
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
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {view && state.data && (
          <>
            {props.latestTo && (
              <p role="status">
                {text.retained} · {text.revision} {state.data.selected.summary.version}
              </p>
            )}
            <AppTabs
              id="payroll-amendment"
              label={text.details}
              selected={props.tab}
              onSelected={props.onTab}
              tabs={[
                { id: "overview", label: text.overview },
                { id: "proposal", label: text.proposal },
                { id: "calculation", label: text.calculation },
                { id: "history", label: text.history },
              ]}
            />
            <div
              role="tabpanel"
              id="payroll-amendment-panel"
              aria-labelledby={`payroll-amendment-tab-${props.tab}`}
            >
              {props.tab === "overview" && (
                <>
                  <AppPropertyList title={text.summary} items={view.overview} />
                  {view.review.length > 0 && (
                    <AppPropertyList title={text.review} items={view.review} />
                  )}
                </>
              )}
              {props.tab === "proposal" && (
                <PayrollAmendmentProposalSection view={view} locale={locale} />
              )}
              {props.tab === "calculation" && (
                <PayrollAmendmentCalculationSection
                  calculation={state.data.calculation}
                  view={view}
                  locale={locale}
                  basisTo={props.basisTo}
                  onMonth={props.onMonth}
                />
              )}
              {props.tab === "history" && (
                <>
                  {view.history.length ? (
                    <AppResourceTable
                      title={text.history}
                      minWidth={880}
                      columns={[
                        { id: "revision", label: text.revision, numeric: true },
                        { id: "action", label: text.action },
                        { id: "status", label: text.status },
                        { id: "recorded", label: text.recorded },
                        { id: "reason", label: text.reason },
                      ]}
                      rows={view.history}
                      action={{ label: text.inspect, onOpen: props.onRevision }}
                    />
                  ) : (
                    <p role="status">{text.noRecords}</p>
                  )}
                  <fieldset className="app-pagination" aria-label={text.historyPages}>
                    <AppButton onClick={props.onFirst} disabled={props.first}>
                      {text.first}
                    </AppButton>
                    <AppButton
                      onClick={props.onNext}
                      disabled={state.data.history.nextCursor === null}
                    >
                      {text.next}
                    </AppButton>
                  </fieldset>
                </>
              )}
            </div>
          </>
        )}
      </div>
    </section>
  );
}
