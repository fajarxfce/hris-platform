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
import type {
  PayrollAmendmentReviewAction,
  PayrollAmendmentReviewDetails,
} from "../../domain/entities/payroll-amendment-review-details";
import { payrollAmendmentMessages } from "../i18n/payroll-amendment-messages";
import { payrollAmendmentReviewMessages } from "../i18n/payroll-amendment-review-messages";
import { payrollReviewMessages } from "../i18n/payroll-review-messages";
import type { PayrollAmendmentReviewReadState } from "../models/payroll-amendment-review-state";
import type { payrollAmendmentReviewView } from "../models/payroll-amendment-review-view";

export function PayrollAmendmentReviewPage(props: {
  state: PayrollAmendmentReviewReadState<PayrollAmendmentReviewDetails>;
  view: ReturnType<typeof payrollAmendmentReviewView> | null;
  locale: Locale;
  companyName: string;
  backTo: string | null;
  proposalTo: string | null;
  basisTo: string | null;
  actionLinks: readonly Readonly<{ action: PayrollAmendmentReviewAction; to: string }>[];
  tab: string;
  onRefresh: () => void;
  onTab: (tab: string) => void;
  onRevision: (revision: string) => void;
}) {
  const { state, locale, view } = props,
    text = payrollAmendmentReviewMessages(locale),
    review = payrollReviewMessages(locale),
    amendment = payrollAmendmentMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={state.data ? `${text.review} ${state.data.review.number}` : text.review}
        context={props.companyName}
        actions={props.backTo && <Link to={props.backTo}>{text.back}</Link>}
      />
      <AppCommandBar label={text.review}>
        {props.actionLinks.map((link) => (
          <Link key={link.action} to={link.to}>
            {text[link.action]}
          </Link>
        ))}
        {props.proposalTo && <Link to={props.proposalTo}>{text.proposal}</Link>}
        {props.basisTo && <Link to={props.basisTo}>{text.basis}</Link>}
        <AppButton
          appearance="subtle"
          icon={<ArrowSync20Regular />}
          disabled={state.stage === "loading"}
          onClick={props.onRefresh}
        >
          {messages(locale).refresh}
        </AppButton>
      </AppCommandBar>
      <div className="app-report-content">
        <AppFailure failure={state.failure} locale={locale} />
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.data && view && (
          <>
            {state.data.approval.status === "BLOCKED" && <p role="status">{review.blocked}</p>}
            <AppTabs
              id="payroll-amendment-review"
              label={text.review}
              selected={props.tab}
              onSelected={props.onTab}
              tabs={[
                { id: "overview", label: review.overview },
                { id: "stages", label: review.stages },
                { id: "history", label: review.history },
              ]}
            />
            <div
              role="tabpanel"
              id="payroll-amendment-review-panel"
              aria-labelledby={`payroll-amendment-review-tab-${props.tab}`}
            >
              {props.tab === "overview" && (
                <>
                  <AppPropertyList title={review.overview} items={view.properties} />
                  <AppPropertyList title={review.totals} items={view.totals} />
                </>
              )}
              {props.tab === "stages" && (
                <AppResourceTable
                  title={review.stages}
                  columns={[
                    { id: "step", label: review.stage },
                    { id: "assignees", label: review.assignees },
                    { id: "status", label: review.status },
                  ]}
                  rows={view.stages}
                />
              )}
              {props.tab === "history" && (
                <AppResourceTable
                  title={review.history}
                  minWidth={1180}
                  columns={[
                    { id: "revision", label: amendment.revision, numeric: true },
                    { id: "action", label: review.action },
                    { id: "status", label: review.status },
                    { id: "step", label: review.stage },
                    { id: "actor", label: review.actor },
                    { id: "behalf", label: review.onBehalf },
                    { id: "at", label: `${review.recorded} (${state.data.review.timezone})` },
                    { id: "reason", label: review.reason },
                  ]}
                  rows={view.history}
                  action={{
                    label: amendment.inspect,
                    onOpen: props.onRevision,
                    columnId: "revision",
                  }}
                />
              )}
            </div>
          </>
        )}
      </div>
    </section>
  );
}
