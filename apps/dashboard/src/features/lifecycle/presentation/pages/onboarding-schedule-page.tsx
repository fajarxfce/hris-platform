import { MessageBar, MessageBarBody } from "@fluentui/react-components";
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
import { onboardingScheduleMessages } from "../i18n/onboarding-schedule-messages";
import type { OnboardingScheduleState } from "../models/onboarding-schedule-state";
import type { onboardingScheduleView } from "../models/onboarding-schedule-view";
export function OnboardingSchedulePage(props: {
  state: OnboardingScheduleState;
  view: ReturnType<typeof onboardingScheduleView> | null;
  companyName: string;
  locale: Locale;
  backTo: string;
  employeeTo: string;
  caseTo: string | null;
  cancelTo: string | null;
  recoverTo: string | null;
  jobTo: string | null;
  onRefresh: () => void;
}) {
  const text = onboardingScheduleMessages(props.locale),
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
        {props.caseTo && <Link to={props.caseTo}>{text.viewChecklist}</Link>}
        <Link to={props.employeeTo}>{text.back}</Link>
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
          <AppResourceTable
            title={text.checklist}
            columns={[
              { id: "task", label: text.task },
              { id: "required", label: text.required },
              { id: "due", label: text.dueDate },
              { id: "assignee", label: text.assignee },
            ]}
            rows={props.view.tasks}
          />
          <p className="app-muted">{text.queuedNote}</p>
          <AppPropertyList title={text.job} items={props.view.job} />
          <AppFailure failure={props.view.issue} locale={props.locale} />
          <details>
            <summary>{text.references}</summary>
            <AppPropertyList title={text.references} items={props.view.references} />
            {props.view.assignees.length > 0 && (
              <AppPropertyList title={text.assigneeReferences} items={props.view.assignees} />
            )}
          </details>
        </>
      )}
    </section>
  );
}
