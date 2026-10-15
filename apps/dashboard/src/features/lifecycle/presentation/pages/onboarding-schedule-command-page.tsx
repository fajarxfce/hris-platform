import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import type { Failure } from "../../../../core/domain/result";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useLifecycleScheduleReasonForm } from "../controllers/use-lifecycle-schedule-reason-form";
import { onboardingScheduleMessages } from "../i18n/onboarding-schedule-messages";
import type { OnboardingScheduleCommandState } from "../models/onboarding-schedule-state";
export function OnboardingScheduleCommandPage(props: {
  state: OnboardingScheduleCommandState<unknown>;
  form: ReturnType<typeof useLifecycleScheduleReasonForm>;
  companyName: string;
  locale: Locale;
  title: string;
  note: string;
  canSubmit: boolean;
  properties: readonly Readonly<{ label: string; value: string }>[];
  issues: readonly Failure[];
  backTo: string;
  backLabel: string;
  successTo: string | null;
  successMessage: string;
  onRetry: () => void;
}) {
  const { state, form, locale } = props,
    text = onboardingScheduleMessages(locale);
  return (
    <section
      className="app-report-content app-record-editor"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={props.title}
        context={props.companyName}
        actions={<Link to={props.backTo}>{props.backLabel}</Link>}
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.stage === "saving" && <AppLoading label={text.saving} />}
      {state.stage === "unconfirmed" && (
        <MessageBar intent="warning" role="status">
          <MessageBarBody>
            {text.unconfirmed}
            <div className="app-reference">
              {text.operation}: {state.operationId}
            </div>
            <AppButton onClick={props.onRetry}>{text.retry}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "saved" && (
        <MessageBar intent="success" role="status">
          <MessageBarBody>
            {props.successMessage}{" "}
            {props.successTo && <Link to={props.successTo}>{text.view}</Link>}
          </MessageBarBody>
        </MessageBar>
      )}
      {["reviewing", "conflict", "unavailable"].includes(state.stage) && (
        <div>
          <AppButton appearance="subtle" icon={<ArrowClockwise20Regular />} onClick={form.reload}>
            {text.reload}
          </AppButton>
        </div>
      )}
      {state.review !== null && <AppPropertyList title={text.review} items={props.properties} />}
      {props.issues.map((issue) => (
        <AppFailure key={issue.code} failure={issue} locale={locale} />
      ))}
      {state.stage === "reviewing" && !props.canSubmit && props.issues.length === 0 && (
        <MessageBar intent="warning" role="status">
          <MessageBarBody>{text.unavailableAction}</MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "reviewing" && props.canSubmit && (
        <form
          className="app-editor-form"
          aria-label={props.title}
          onSubmit={form.submit}
          noValidate
        >
          <p>{props.note}</p>
          <AppTextArea label={text.reason} rows={3} maxLength={1000} required {...form.reason} />
          <div className="app-form-actions">
            <AppButton appearance="primary" type="submit">
              {props.title}
            </AppButton>
          </div>
        </form>
      )}
    </section>
  );
}
