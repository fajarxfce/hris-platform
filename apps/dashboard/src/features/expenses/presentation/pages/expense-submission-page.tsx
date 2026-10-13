import { MessageBar, MessageBarBody, Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTabs } from "../../../../core/presentation/components/app-tabs";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { ExpenseReceiptList } from "../components/expense-receipt-list";
import { expenseClaimMessages } from "../i18n/expense-claim-messages";
import type { ExpenseSubmissionState } from "../models/expense-submission-state";
import type { expenseSubmissionView } from "../models/expense-submission-view";
export function ExpenseSubmissionPage(props: {
  state: ExpenseSubmissionState;
  view: ReturnType<typeof expenseSubmissionView> | null;
  companyName: string;
  locale: Locale;
  backTo: string;
  backLabel: string;
  approvalTo: string | null;
  tab: "items" | "reviews" | "approval";
  onRefresh: () => void;
  onTab: (tab: string) => void;
  onLine: (id: string | null) => void;
  onReview: (version: string | null) => void;
  onDownload: (id: string) => void;
  onCancelDownload: () => void;
}) {
  const { state, view, locale } = props,
    text = expenseClaimMessages(locale);
  return (
    <>
      <section className="app-expense-submission" aria-busy={state.stage === "loading"}>
        <AppPageHeader
          title={view?.title ?? text.submission}
          context={props.companyName}
          actions={
            <>
              <Link to={props.backTo}>{props.backLabel}</Link>
              <AppButton
                appearance="subtle"
                icon={<ArrowClockwise20Regular />}
                onClick={props.onRefresh}
                disabled={state.stage === "loading"}
              >
                {messages(locale).refresh}
              </AppButton>
            </>
          }
        />
        <AppFailure failure={state.failure} locale={locale} />
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {view && (
          <>
            <div className="app-report-content">
              <AppPropertyList title={text.submission} items={view.properties} />
              {view.description && <p className="app-expense-description">{view.description}</p>}
              <p className="app-muted">{text.frozen}</p>
              {view.duplicate && (
                <MessageBar intent="warning">
                  <MessageBarBody>{text.duplicateNote}</MessageBarBody>
                </MessageBar>
              )}
            </div>
            <AppTabs
              id="expense-submission"
              label={text.submission}
              tabs={[
                { id: "items", label: text.items },
                { id: "reviews", label: text.reviews },
                { id: "approval", label: text.approval },
              ]}
              selected={props.tab}
              onSelected={props.onTab}
            />
            <div
              id="expense-submission-panel"
              role="tabpanel"
              aria-labelledby={`expense-submission-tab-${props.tab}`}
              className="app-report-content"
            >
              {props.tab === "items" && (
                <AppResourceTable
                  title={text.items}
                  minWidth={900}
                  columns={[
                    { id: "date", label: text.date },
                    { id: "category", label: text.category },
                    { id: "description", label: text.description },
                    { id: "amount", label: text.amount, numeric: true },
                    { id: "receipts", label: text.receipts, numeric: true },
                  ]}
                  rows={view.lines}
                  action={{ label: text.viewLine, onOpen: props.onLine }}
                />
              )}
              {props.tab === "reviews" &&
                (view.reviews.length === 0 ? (
                  <Text role="status">{text.emptyReviews}</Text>
                ) : (
                  <AppResourceTable
                    title={text.reviews}
                    minWidth={780}
                    columns={[
                      { id: "stage", label: text.stage, numeric: true },
                      { id: "decision", label: text.decision },
                      { id: "status", label: text.status },
                      { id: "recorded", label: text.recorded },
                    ]}
                    rows={view.reviews}
                    action={{ label: text.viewChange, onOpen: props.onReview }}
                  />
                ))}
              {props.tab === "approval" && (
                <>
                  <AppPropertyList title={text.approval} items={view.approval} />
                  {props.approvalTo && <Link to={props.approvalTo}>{text.openApproval}</Link>}
                  <AppResourceTable
                    title={text.stage}
                    minWidth={640}
                    columns={[
                      { id: "stage", label: text.stage, numeric: true },
                      { id: "status", label: text.status },
                      { id: "assigned", label: text.assigned },
                    ]}
                    rows={view.stages}
                  />
                </>
              )}
            </div>
          </>
        )}
      </section>
      <AppDetailsPanel
        open={view?.line != null}
        title={text.lineDetails}
        closeLabel={messages(locale).close}
        onClose={() => props.onLine(null)}
      >
        {view?.line && (
          <>
            <AppPropertyList title={text.lineDetails} items={view.line.properties} />
            <ExpenseReceiptList
              rows={view.line.receipts}
              state={state.download}
              locale={locale}
              onDownload={props.onDownload}
              onCancel={props.onCancelDownload}
            />
          </>
        )}
      </AppDetailsPanel>
      <AppDetailsPanel
        open={view?.review != null}
        title={text.reviewDetails}
        closeLabel={messages(locale).close}
        onClose={() => props.onReview(null)}
      >
        {view?.review && <AppPropertyList title={text.reviews} items={view.review} />}
      </AppDetailsPanel>
    </>
  );
}
