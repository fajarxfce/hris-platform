import { Text } from "@fluentui/react-components";
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
import type { useExpenseClaim } from "../controllers/use-expense-claim";
import { expenseClaimMessages } from "../i18n/expense-claim-messages";
import { expenseDraftMessages } from "../i18n/expense-draft-messages";
import { type ExpenseClaimTab, expenseClaimTabs } from "../models/expense-claim-route";
import type { expenseClaimView } from "../models/expense-claim-view";
export function ExpenseClaimPage(props: {
  state: ReturnType<typeof useExpenseClaim>;
  view: ReturnType<typeof expenseClaimView> | null;
  locale: Locale;
  companyName: string;
  backTo: string;
  editTo: string | null;
  tab: ExpenseClaimTab;
  firstHistory: boolean;
  onTab: (tab: string) => void;
  onSubmission: (id: string) => void;
  onChange: (version: string | null) => void;
  onLatest: () => void;
  onOlder: () => void;
}) {
  const { state, view, locale } = props,
    text = expenseClaimMessages(locale);
  return (
    <>
      <section className="app-expense-claim" aria-busy={state.loading}>
        <AppPageHeader
          title={view?.title ?? text.details}
          context={props.companyName}
          actions={
            <>
              <Link to={props.backTo}>{text.title}</Link>
              {props.editTo && <Link to={props.editTo}>{expenseDraftMessages(locale).edit}</Link>}
              <AppButton
                appearance="subtle"
                icon={<ArrowClockwise20Regular />}
                onClick={state.refresh}
                disabled={state.loading}
              >
                {messages(locale).refresh}
              </AppButton>
            </>
          }
        />
        <AppFailure failure={state.failure} locale={locale} />
        {state.loading && <AppLoading label={messages(locale).loading} />}
        {view && (
          <>
            <AppTabs
              id="expense-claim"
              label={text.details}
              tabs={expenseClaimTabs.map((id) => ({ id, label: text[id] }))}
              selected={props.tab}
              onSelected={props.onTab}
            />
            <div
              id="expense-claim-panel"
              role="tabpanel"
              aria-labelledby={`expense-claim-tab-${props.tab}`}
              className="app-report-content"
            >
              {props.tab === "overview" && (
                <>
                  <AppPropertyList title={text.currentDraft} items={view.properties} />
                  {view.description && (
                    <p className="app-expense-description">{view.description}</p>
                  )}
                  <p className="app-muted">{text.draftNote}</p>
                  {view.lines.length === 0 ? (
                    <Text role="status">{text.emptyLines}</Text>
                  ) : (
                    <AppResourceTable
                      title={text.items}
                      minWidth={760}
                      columns={[
                        { id: "date", label: text.date },
                        { id: "description", label: text.description },
                        { id: "amount", label: text.amount, numeric: true },
                        { id: "receipts", label: text.receipts, numeric: true },
                      ]}
                      rows={view.lines}
                    />
                  )}
                </>
              )}
              {props.tab === "submissions" &&
                (view.submissions.length === 0 ? (
                  <Text role="status">{text.emptySubmissions}</Text>
                ) : (
                  <AppResourceTable
                    title={text.submissions}
                    minWidth={850}
                    columns={[
                      { id: "number", label: text.number, numeric: true },
                      { id: "title", label: text.claimTitle },
                      { id: "total", label: text.total, numeric: true },
                      { id: "submitted", label: text.submitted },
                    ]}
                    rows={view.submissions}
                    action={{ label: text.viewSubmission, onOpen: props.onSubmission }}
                  />
                ))}
              {props.tab === "history" && (
                <>
                  {view.history.length === 0 ? (
                    <Text role="status">{text.emptyHistory}</Text>
                  ) : (
                    <AppResourceTable
                      title={text.history}
                      minWidth={780}
                      columns={[
                        { id: "version", label: text.version, numeric: true },
                        { id: "action", label: text.action },
                        { id: "status", label: text.status },
                        { id: "recorded", label: text.recorded },
                      ]}
                      rows={view.history}
                      action={{ label: text.viewChange, onOpen: props.onChange }}
                    />
                  )}
                  <fieldset className="app-pagination" aria-label={text.historyPages}>
                    <AppButton
                      appearance="subtle"
                      disabled={props.firstHistory || state.loading}
                      onClick={props.onLatest}
                    >
                      {text.latest}
                    </AppButton>
                    <AppButton
                      appearance="subtle"
                      disabled={state.review?.history.nextCursor == null || state.loading}
                      onClick={props.onOlder}
                    >
                      {text.older}
                    </AppButton>
                  </fieldset>
                </>
              )}
              {props.tab === "payments" &&
                (view.payments.length === 0 ? (
                  <Text role="status">{text.emptyPayments}</Text>
                ) : (
                  <AppResourceTable
                    title={text.payments}
                    minWidth={720}
                    columns={[
                      { id: "status", label: text.status },
                      { id: "created", label: text.paymentCreated },
                      { id: "released", label: text.released },
                      { id: "settled", label: text.settled },
                    ]}
                    rows={view.payments}
                  />
                ))}
            </div>
          </>
        )}
      </section>
      <AppDetailsPanel
        open={view?.change != null}
        title={text.changeDetails}
        closeLabel={messages(locale).close}
        onClose={() => props.onChange(null)}
      >
        {view?.change && <AppPropertyList title={text.history} items={view.change} />}
      </AppDetailsPanel>
    </>
  );
}
