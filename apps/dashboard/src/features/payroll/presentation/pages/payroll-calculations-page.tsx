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
import { payrollCalculationMessages } from "../i18n/payroll-calculation-messages";
import type { PayrollCalculationsState } from "../models/payroll-calculation-state";
import type {
  payrollCalculationReviewView,
  payrollRunRows,
} from "../models/payroll-calculation-view";
export function PayrollCalculationsPage(props: {
  state: PayrollCalculationsState;
  properties: ReturnType<typeof payrollCalculationReviewView>;
  rows: ReturnType<typeof payrollRunRows>;
  locale: Locale;
  companyName: string;
  backTo: string;
  startTo: string | null;
  onRefresh: () => void;
  onRun: (id: string) => void;
}) {
  const { state, locale } = props,
    text = payrollCalculationMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={
          state.data
            ? `${text.calculations} · ${state.data.review.period.earningsMonth}`
            : text.calculations
        }
        context={props.companyName}
        actions={<Link to={props.backTo}>{text.back}</Link>}
      />
      <AppCommandBar label={text.calculations}>
        <AppButton
          appearance="subtle"
          icon={<ArrowSync20Regular />}
          disabled={state.stage === "loading"}
          onClick={props.onRefresh}
        >
          {messages(locale).refresh}
        </AppButton>
        {props.startTo && <Link to={props.startTo}>{text.start}</Link>}
      </AppCommandBar>
      <div className="app-report-content">
        <AppFailure failure={state.failure} locale={locale} />
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.data && (
          <>
            <AppPropertyList
              title={state.data.review.period.status === "DRAFT" ? text.review : text.period}
              items={props.properties}
            />
            {state.data.review.period.status === "DRAFT" &&
              state.data.review.blockingIssues.map((issue) => (
                <AppFailure key={issue.code} failure={issue} locale={locale} />
              ))}
            {props.rows.length === 0 ? (
              <p>{text.noRuns}</p>
            ) : (
              <AppResourceTable
                title={text.runs}
                columns={[
                  { id: "number", label: text.number },
                  { id: "started", label: text.created },
                  { id: "status", label: text.status },
                  { id: "processed", label: text.processed, numeric: true },
                  { id: "failed", label: text.failed, numeric: true },
                ]}
                rows={props.rows}
                action={{ label: text.openRun, onOpen: props.onRun }}
              />
            )}
          </>
        )}
      </div>
    </section>
  );
}
