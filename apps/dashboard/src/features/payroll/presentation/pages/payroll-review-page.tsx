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
import type { PayrollReviewAction } from "../../domain/entities/payroll-review-command";
import { payrollReviewMessages } from "../i18n/payroll-review-messages";
import type { PayrollReviewState } from "../models/payroll-review-state";
import type { payrollReviewView } from "../models/payroll-review-view";
export function PayrollReviewPage(props: {
  state: PayrollReviewState;
  view: ReturnType<typeof payrollReviewView> | null;
  locale: Locale;
  companyName: string;
  backTo: string | null;
  runTo: string | null;
  actionLinks: readonly Readonly<{ action: PayrollReviewAction; to: string }>[];
  tab: string;
  onRefresh: () => void;
  onTab: (tab: string) => void;
}) {
  const { state, locale, view } = props,
    text = payrollReviewMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={state.data ? `${text.review} ${state.data.review.number}` : text.review}
        context={props.companyName}
        actions={props.backTo && <Link to={props.backTo}>{text.back}</Link>}
      />
      <AppCommandBar label={text.review}>
        <AppButton
          appearance="subtle"
          icon={<ArrowSync20Regular />}
          disabled={state.stage === "loading"}
          onClick={props.onRefresh}
        >
          {messages(locale).refresh}
        </AppButton>
        {props.runTo && <Link to={props.runTo}>{text.run}</Link>}
        {props.actionLinks.map((link) => (
          <Link key={link.action} to={link.to}>
            {text[link.action]}
          </Link>
        ))}
      </AppCommandBar>
      <div className="app-report-content">
        <AppFailure failure={state.failure} locale={locale} />
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.data && view && (
          <>
            {state.data.approval.status === "BLOCKED" && <p role="status">{text.blocked}</p>}
            {state.data.finalizationActive && <p role="status">{text.finalizing}</p>}
            <AppTabs
              id="payroll-review"
              label={text.review}
              selected={props.tab}
              onSelected={props.onTab}
              tabs={[
                { id: "overview", label: text.overview },
                { id: "stages", label: text.stages },
                { id: "history", label: text.history },
              ]}
            />
            <div
              role="tabpanel"
              id="payroll-review-panel"
              aria-labelledby={`payroll-review-tab-${props.tab}`}
            >
              {props.tab === "overview" && (
                <>
                  <AppPropertyList title={text.overview} items={view.properties} />
                  <AppPropertyList title={text.totals} items={view.totals} />
                </>
              )}
              {props.tab === "stages" && (
                <AppResourceTable
                  title={text.stages}
                  columns={[
                    { id: "step", label: text.stage },
                    { id: "assignees", label: text.assignees },
                    { id: "status", label: text.status },
                  ]}
                  rows={view.stages}
                />
              )}
              {props.tab === "history" && (
                <AppResourceTable
                  title={text.history}
                  minWidth={1060}
                  columns={[
                    { id: "action", label: text.action },
                    { id: "status", label: text.status },
                    { id: "step", label: text.stage },
                    { id: "actor", label: text.actor },
                    { id: "behalf", label: text.onBehalf },
                    { id: "at", label: text.recorded },
                    { id: "reason", label: text.reason },
                  ]}
                  rows={view.history}
                />
              )}
            </div>
          </>
        )}
      </div>
    </section>
  );
}
