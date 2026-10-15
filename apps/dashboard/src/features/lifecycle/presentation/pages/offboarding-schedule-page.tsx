import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { offboardingScheduleMessages } from "../i18n/offboarding-schedule-messages";
import type { OffboardingScheduleState } from "../models/offboarding-schedule-state";
import type { offboardingScheduleView } from "../models/offboarding-schedule-view";
export function OffboardingSchedulePage(props: {
  state: OffboardingScheduleState;
  view: ReturnType<typeof offboardingScheduleView> | null;
  companyName: string;
  locale: Locale;
  backTo: string;
  caseTo: string;
  cancelTo: string | null;
  recoverTo: string | null;
  jobTo: string | null;
  onRefresh: () => void;
}) {
  const text = offboardingScheduleMessages(props.locale),
    shared = messages(props.locale);
  return (
    <section
      className="app-report-content"
      aria-busy={props.state.stage === "loading" || props.state.stage === "updating"}
    >
      <AppPageHeader
        title={text.detail}
        context={props.companyName}
        actions={<Link to={props.backTo}>{text.backToSchedules}</Link>}
      />
      <AppCommandBar label={text.detail}>
        {props.cancelTo && <Link to={props.cancelTo}>{text.cancel}</Link>}
        {props.recoverTo && <Link to={props.recoverTo}>{text.recover}</Link>}
        <Link to={props.caseTo}>{text.back}</Link>
        {props.jobTo && <Link to={props.jobTo}>{text.jobLink}</Link>}
        <AppButton
          appearance="subtle"
          icon={<ArrowClockwise20Regular />}
          disabled={props.state.stage === "loading" || props.state.stage === "updating"}
          onClick={props.onRefresh}
        >
          {shared.refresh}
        </AppButton>
      </AppCommandBar>
      <AppFailure failure={props.state.failure} locale={props.locale} />
      {props.state.stage === "loading" && <AppLoading label={shared.loading} />}
      {props.state.updatesPaused && (
        <MessageBar intent="info" role="status">
          <MessageBarBody>{text.paused}</MessageBarBody>
        </MessageBar>
      )}
      {props.view && (
        <>
          <AppPropertyList title={text.overview} items={props.view.properties} />
          <p>{text.queuedNote}</p>
          <AppPropertyList title={text.job} items={props.view.job} />
          <AppFailure failure={props.view.issue} locale={props.locale} />
          <details>
            <summary>{text.references}</summary>
            <AppPropertyList title={text.references} items={props.view.references} />
          </details>
        </>
      )}
    </section>
  );
}
