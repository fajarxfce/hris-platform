import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { ArrowSync20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import type { Failure } from "../../../../core/domain/result";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { payrollFinalizationMessages } from "../i18n/payroll-finalization-messages";
import type { PayrollFinalizationsState } from "../models/payroll-finalization-state";
import type {
  payrollFinalizationPreparationView,
  payrollFinalizationRows,
} from "../models/payroll-finalization-view";
export function PayrollFinalizationsPage(props: {
  state: PayrollFinalizationsState;
  properties: ReturnType<typeof payrollFinalizationPreparationView>;
  rows: ReturnType<typeof payrollFinalizationRows>;
  issues: readonly Failure[];
  published: boolean;
  locale: Locale;
  companyName: string;
  backTo: string;
  startTo: string | null;
  runTo: string | null;
  reviewTo: string | null;
  onRefresh: () => void;
  onOpen: (id: string) => void;
}) {
  const { state, locale } = props,
    text = payrollFinalizationMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.overview}
        context={props.companyName}
        actions={<Link to={props.backTo}>{text.back}</Link>}
      />
      <AppCommandBar label={text.overview}>
        <AppButton
          appearance="subtle"
          icon={<ArrowSync20Regular />}
          disabled={state.stage === "loading"}
          onClick={props.onRefresh}
        >
          {messages(locale).refresh}
        </AppButton>
        {props.startTo && <Link to={props.startTo}>{text.reviewPublication}</Link>}
        {props.runTo && <Link to={props.runTo}>{text.run}</Link>}
        {props.reviewTo && <Link to={props.reviewTo}>{text.review}</Link>}
      </AppCommandBar>
      <div className="app-report-content">
        <AppFailure failure={state.failure} locale={locale} />
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.data && (
          <>
            {props.published && (
              <MessageBar intent="success" role="status">
                <MessageBarBody>
                  {text.publicationComplete} {text.paymentNote}
                </MessageBarBody>
              </MessageBar>
            )}
            <AppPropertyList title={text.totals} items={props.properties} />
            {props.issues.map((issue) => (
              <AppFailure key={issue.code} failure={issue} locale={locale} />
            ))}
            {props.rows.length === 0 ? (
              <p>{text.noAttempts}</p>
            ) : (
              <AppResourceTable
                title={text.history}
                columns={[
                  { id: "attempt", label: text.attempt },
                  { id: "requested", label: text.requested },
                  { id: "published", label: text.published },
                  { id: "reason", label: text.reason },
                ]}
                rows={props.rows}
                action={{ label: text.openPublication, onOpen: props.onOpen }}
              />
            )}
          </>
        )}
      </div>
    </section>
  );
}
