import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { leaveBatchMessages } from "../i18n/leave-batch-messages";
import type { LeaveBatchesState } from "../models/leave-batch-state";
import type { leaveBatchesView } from "../models/leave-batch-view";
export function LeaveBatchesPage(props: {
  state: LeaveBatchesState;
  rows: ReturnType<typeof leaveBatchesView>;
  locale: Locale;
  companyName: string;
  accrualTo: string | null;
  closeTo: string | null;
  firstPage: boolean;
  onRefresh: () => void;
  onOpen: (id: string) => void;
  onFirst: () => void;
  onNext: () => void;
}) {
  const { state, locale } = props,
    text = leaveBatchMessages(locale),
    shared = messages(locale);
  return (
    <section className="app-report-content" aria-busy={state.stage === "loading"}>
      <AppPageHeader title={text.title} context={props.companyName} />
      <AppCommandBar label={text.title}>
        {props.accrualTo && <Link to={props.accrualTo}>{text.newAccrual}</Link>}
        {props.closeTo && <Link to={props.closeTo}>{text.newYearClose}</Link>}
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
              { id: "kind", label: text.kind },
              { id: "period", label: text.period },
              { id: "status", label: text.status },
              { id: "employees", label: text.employees, numeric: true },
              { id: "requested", label: text.createdAt },
            ]}
            rows={props.rows}
            action={{ label: text.view, columnId: "type", onOpen: props.onOpen }}
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
          disabled={!state.page?.nextCursor || state.stage === "loading"}
          onClick={props.onNext}
        >
          {text.next}
        </AppButton>
      </fieldset>
    </section>
  );
}
