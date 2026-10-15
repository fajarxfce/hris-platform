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
import type { PayrollAmendmentReviews } from "../../domain/entities/payroll-amendment-review-details";
import { payrollAmendmentMessages } from "../i18n/payroll-amendment-messages";
import { payrollAmendmentReviewMessages } from "../i18n/payroll-amendment-review-messages";
import { payrollReviewMessages } from "../i18n/payroll-review-messages";
import type { PayrollAmendmentReadState } from "../models/payroll-amendment-review-state";
import type {
  payrollAmendmentReviewPreparationView,
  payrollAmendmentReviewRows,
} from "../models/payroll-amendment-review-view";

export function PayrollAmendmentReviewsPage(props: {
  state: PayrollAmendmentReadState<PayrollAmendmentReviews>;
  properties: ReturnType<typeof payrollAmendmentReviewPreparationView>;
  rows: ReturnType<typeof payrollAmendmentReviewRows>;
  locale: Locale;
  companyName: string;
  backTo: string;
  submitTo: string | null;
  onRefresh: () => void;
  onReview: (id: string) => void;
}) {
  const { state, locale } = props,
    text = payrollAmendmentReviewMessages(locale),
    review = payrollReviewMessages(locale),
    amendment = payrollAmendmentMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={props.companyName}
        actions={<Link to={props.backTo}>{text.proposal}</Link>}
      />
      <AppCommandBar label={text.title}>
        {props.submitTo && <Link to={props.submitTo}>{text.submit}</Link>}
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
        {state.data && (
          <>
            <AppPropertyList title={amendment.summary} items={props.properties} />
            {props.rows.length === 0 ? (
              <p role="status">{text.empty}</p>
            ) : (
              <AppResourceTable
                title={text.title}
                minWidth={880}
                columns={[
                  { id: "round", label: review.round },
                  { id: "status", label: review.status },
                  { id: "exposure", label: amendment.exposure, numeric: true },
                  { id: "at", label: review.submitted },
                  { id: "reason", label: review.reason },
                ]}
                rows={props.rows}
                action={{ label: text.open, onOpen: props.onReview, columnId: "round" }}
              />
            )}
          </>
        )}
      </div>
    </section>
  );
}
