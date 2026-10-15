import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { leaveCarryExpiryMessages } from "../i18n/leave-carry-expiry-messages";
import type { LeaveCarryExpiryState } from "../models/leave-carry-expiry-state";
import type { leaveCarryExpiryView } from "../models/leave-carry-expiry-view";
export function LeaveCarryExpiryPage(props: {
  state: LeaveCarryExpiryState;
  view: ReturnType<typeof leaveCarryExpiryView> | null;
  locale: Locale;
  companyName: string;
  backTo: string;
  resumeTo: string | null;
  jobTo: string | null;
  closingTo: string | null;
  firstPage: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onLedger: (id: string) => void;
}) {
  const { state, view, locale } = props,
    text = leaveCarryExpiryMessages(locale),
    shared = messages(locale);
  return (
    <section className="app-report-content" aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.detail}
        context={props.companyName}
        actions={<Link to={props.backTo}>{text.back}</Link>}
      />
      <AppCommandBar label={text.detail}>
        <AppButton
          appearance="subtle"
          icon={<ArrowClockwise20Regular />}
          disabled={state.stage === "loading" || state.stage === "updating"}
          onClick={props.onRefresh}
        >
          {shared.refresh}
        </AppButton>
        {props.resumeTo && <Link to={props.resumeTo}>{text.resume}</Link>}
        {props.closingTo && <Link to={props.closingTo}>{text.closing}</Link>}
        {props.jobTo && <Link to={props.jobTo}>{text.job}</Link>}
      </AppCommandBar>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && !view && <AppLoading label={shared.loading} />}
      {state.updatesPaused && (
        <p role="status" className="app-muted">
          {text.paused}
        </p>
      )}
      {view && (
        <>
          <AppPropertyList title={text.overview} items={view.properties} />
          <AppFailure failure={view.issue} locale={locale} />
          {view.rows.length ? (
            <AppResourceTable
              title={text.results}
              columns={[
                { id: "employee", label: text.employee },
                { id: "number", label: text.number },
                { id: "result", label: text.result },
                { id: "days", label: text.expiredDays, numeric: true },
                { id: "recorded", label: text.recordedAt },
              ]}
              rows={view.rows}
              action={{
                label: text.ledger,
                columnId: "employee",
                onOpen: props.onLedger,
                disabled: state.stage === "unavailable",
              }}
            />
          ) : (
            <p role="status">{text.noResults}</p>
          )}
          <fieldset className="app-pagination" aria-label={text.pages}>
            <AppButton
              appearance="subtle"
              disabled={props.firstPage || state.stage === "loading" || state.stage === "updating"}
              onClick={props.onFirst}
            >
              {text.first}
            </AppButton>
            <AppButton
              appearance="subtle"
              disabled={
                !state.details?.results.nextCursor || !["ready", "updating"].includes(state.stage)
              }
              onClick={props.onNext}
            >
              {text.next}
            </AppButton>
          </fieldset>
          <AppResourceTable
            title={text.attempts}
            columns={[
              { id: "number", label: text.attempt },
              { id: "started", label: text.startedAt },
              { id: "completed", label: text.baseCompleted, numeric: true },
              { id: "reason", label: text.reason },
            ]}
            rows={view.attempts}
          />
        </>
      )}
    </section>
  );
}
