import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { leaveCarryExpiryMessages } from "../i18n/leave-carry-expiry-messages";
import type { LeaveCarryExpiriesState } from "../models/leave-carry-expiry-state";
import type { leaveCarryExpiriesView } from "../models/leave-carry-expiry-view";
export function LeaveCarryExpiriesPage(props: {
  state: LeaveCarryExpiriesState;
  rows: ReturnType<typeof leaveCarryExpiriesView>;
  locale: Locale;
  companyName: string;
  processingTo: string;
  firstPage: boolean;
  onRefresh: () => void;
  onOpen: (id: string) => void;
  onFirst: () => void;
  onNext: () => void;
}) {
  const { state, locale } = props,
    text = leaveCarryExpiryMessages(locale),
    shared = messages(locale);
  return (
    <section className="app-report-content" aria-busy={state.stage === "loading"}>
      <AppPageHeader title={text.title} context={props.companyName} />
      <AppCommandBar label={text.title}>
        <Link to={props.processingTo}>{text.processing}</Link>
        <AppButton
          appearance="subtle"
          icon={<ArrowClockwise20Regular />}
          disabled={state.stage === "loading"}
          onClick={props.onRefresh}
        >
          {shared.refresh}
        </AppButton>
      </AppCommandBar>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={shared.loading} />}
      {state.page &&
        (props.rows.length ? (
          <AppResourceTable
            title={text.title}
            columns={[
              { id: "type", label: text.type },
              { id: "year", label: text.year },
              { id: "valid", label: text.validThrough },
              { id: "status", label: text.status },
              { id: "processed", label: text.processed, numeric: true },
              { id: "source", label: text.source },
            ]}
            rows={props.rows}
            action={{
              label: text.view,
              columnId: "type",
              onOpen: props.onOpen,
              disabled: state.stage !== "ready",
            }}
          />
        ) : (
          <p role="status">{text.empty}</p>
        ))}
      <fieldset className="app-pagination" aria-label={text.pages}>
        <AppButton
          appearance="subtle"
          disabled={props.firstPage || state.stage === "loading"}
          onClick={props.onFirst}
        >
          {text.first}
        </AppButton>
        <AppButton
          appearance="subtle"
          disabled={!state.page?.nextCursor || state.stage !== "ready"}
          onClick={props.onNext}
        >
          {text.next}
        </AppButton>
      </fieldset>
    </section>
  );
}
