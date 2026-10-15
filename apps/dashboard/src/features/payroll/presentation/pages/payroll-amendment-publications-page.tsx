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
import type { PayrollAmendmentPublications } from "../../domain/entities/payroll-amendment-publications";
import { payrollAmendmentPublicationMessages } from "../i18n/payroll-amendment-publication-messages";
import type {
  payrollAmendmentPublicationRows,
  payrollAmendmentPublicationSummary,
} from "../models/payroll-amendment-publication-view";
import type { PayrollAmendmentReadState } from "../models/payroll-amendment-review-state";

export function PayrollAmendmentPublicationsPage(props: {
  state: PayrollAmendmentReadState<PayrollAmendmentPublications>;
  properties: ReturnType<typeof payrollAmendmentPublicationSummary>;
  rows: ReturnType<typeof payrollAmendmentPublicationRows>;
  locale: Locale;
  companyName: string;
  backTo: string | null;
  startTo: string | null;
  onRefresh: () => void;
  onPublication: (id: string) => void;
}) {
  const { state, locale } = props,
    text = payrollAmendmentPublicationMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={props.companyName}
        actions={props.backTo && <Link to={props.backTo}>{text.amendment}</Link>}
      />
      <AppCommandBar label={text.title}>
        {props.startTo && <Link to={props.startTo}>{text.start}</Link>}
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
            <AppPropertyList title={text.amendment} items={props.properties} />
            {props.rows.length === 0 ? (
              <p role="status">{text.empty}</p>
            ) : (
              <AppResourceTable
                title={text.title}
                minWidth={800}
                columns={[
                  { id: "attempt", label: text.attempt },
                  { id: "status", label: text.status },
                  { id: "job", label: text.progress },
                  { id: "at", label: text.requestedAt },
                  { id: "reason", label: text.reason },
                ]}
                rows={props.rows}
                action={{ label: text.open, onOpen: props.onPublication, columnId: "attempt" }}
              />
            )}
          </>
        )}
      </div>
    </section>
  );
}
