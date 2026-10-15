import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { offboardingScheduleMessages } from "../i18n/offboarding-schedule-messages";
import type { OffboardingSchedulesState } from "../models/offboarding-schedule-state";
import type { offboardingSchedulesView } from "../models/offboarding-schedule-view";
export function OffboardingSchedulesPage(props: {
  state: OffboardingSchedulesState;
  rows: ReturnType<typeof offboardingSchedulesView>;
  companyName: string;
  locale: Locale;
  backTo: string;
  newTo: string | null;
  onRefresh: () => void;
  onOpen: (id: string) => void;
}) {
  const text = offboardingScheduleMessages(props.locale),
    shared = messages(props.locale);
  return (
    <section className="app-report-content" aria-busy={props.state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={props.companyName}
        actions={<Link to={props.backTo}>{text.back}</Link>}
      />
      <AppCommandBar label={text.title}>
        {props.newTo && <Link to={props.newTo}>{text.new}</Link>}
        <AppButton
          appearance="subtle"
          icon={<ArrowClockwise20Regular />}
          disabled={props.state.stage === "loading"}
          onClick={props.onRefresh}
        >
          {shared.refresh}
        </AppButton>
      </AppCommandBar>
      <AppFailure failure={props.state.failure} locale={props.locale} />
      {props.state.stage === "loading" && <AppLoading label={shared.loading} />}
      {props.state.items &&
        (props.rows.length === 0 ? (
          <Text role="status">{text.empty}</Text>
        ) : (
          <AppResourceTable
            title={text.title}
            columns={[
              { id: "attempt", label: text.attempt, numeric: true },
              { id: "status", label: text.status },
              { id: "scheduledFor", label: text.scheduledFor },
              { id: "requestedAt", label: text.requestedAt },
              { id: "reason", label: text.reason },
            ]}
            rows={props.rows}
            action={{ label: text.view, columnId: "attempt", onOpen: props.onOpen }}
          />
        ))}
    </section>
  );
}
