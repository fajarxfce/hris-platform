import { ArrowSync20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { payrollReviewMessages } from "../i18n/payroll-review-messages";
import type { PayrollReviewsState } from "../models/payroll-review-state";
import type {
  payrollReviewPreparationView,
  payrollReviewRows,
} from "../models/payroll-review-view";
export function PayrollReviewsPage(props: {
  state: PayrollReviewsState;
  properties: ReturnType<typeof payrollReviewPreparationView>;
  rows: ReturnType<typeof payrollReviewRows>;
  locale: Locale;
  companyName: string;
  backTo: string;
  submitTo: string | null;
  onRefresh: () => void;
  onReview: (id: string) => void;
}) {
  const { state, locale } = props,
    text = payrollReviewMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.reviews}
        context={props.companyName}
        actions={<Link to={props.backTo}>{text.run}</Link>}
      />
      <AppCommandBar label={text.reviews}>
        <AppButton
          appearance="subtle"
          icon={<ArrowSync20Regular />}
          disabled={state.stage === "loading"}
          onClick={props.onRefresh}
        >
          {messages(locale).refresh}
        </AppButton>
        {props.submitTo && <Link to={props.submitTo}>{text.submit}</Link>}
      </AppCommandBar>
      <div className="app-report-content">
        <AppFailure failure={state.failure} locale={locale} />
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.data && (
          <>
            <AppPropertyList title={text.totals} items={props.properties} />
            {state.data.preparation.run.succeeded !== state.data.preparation.run.totalEmployees && (
              <p>{text.totalNote}</p>
            )}
            {state.data.preparation.blockingIssues.map((issue) => (
              <AppFailure key={issue.code} failure={issue} locale={locale} />
            ))}
            {props.rows.length === 0 ? (
              <p>{text.noReviews}</p>
            ) : (
              <AppResourceTable
                title={text.history}
                columns={[
                  { id: "round", label: text.round },
                  { id: "status", label: text.status },
                  { id: "at", label: text.submitted },
                  { id: "reason", label: text.reason },
                ]}
                rows={props.rows}
                action={{ label: text.openReview, onOpen: props.onReview }}
              />
            )}
          </>
        )}
      </div>
    </section>
  );
}
