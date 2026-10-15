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
import { onboardingScheduleMessages } from "../i18n/onboarding-schedule-messages";
import type { OnboardingSchedulesState } from "../models/onboarding-schedule-state";
import type { onboardingSchedulesView } from "../models/onboarding-schedule-view";
export function OnboardingSchedulesPage(props: {
  state: OnboardingSchedulesState;
  rows: ReturnType<typeof onboardingSchedulesView>;
  context: string;
  locale: Locale;
  backTo: string;
  newTo: string | null;
  onRefresh: () => void;
  onOpen: (id: string) => void;
}) {
  const text = onboardingScheduleMessages(props.locale),
    shared = messages(props.locale);
  return (
    <section className="app-report-content" aria-busy={props.state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={props.context}
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
              { id: "template", label: text.template },
              { id: "startsOn", label: text.startsOn },
              { id: "targetDate", label: text.firstDay },
            ]}
            rows={props.rows}
            action={{ label: text.view, columnId: "attempt", onOpen: props.onOpen }}
          />
        ))}
    </section>
  );
}
