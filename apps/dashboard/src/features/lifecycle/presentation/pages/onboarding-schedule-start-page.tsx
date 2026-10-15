import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useOnboardingScheduleForm } from "../controllers/use-onboarding-schedule-form";
import { onboardingScheduleMessages } from "../i18n/onboarding-schedule-messages";
import type { OnboardingScheduleStartState } from "../models/onboarding-schedule-state";
import type {
  onboardingScheduleReviewView,
  onboardingTaskRows,
} from "../models/onboarding-schedule-view";
export function OnboardingScheduleStartPage(props: {
  state: OnboardingScheduleStartState;
  form: ReturnType<typeof useOnboardingScheduleForm>;
  view: ReturnType<typeof onboardingScheduleReviewView> | null;
  rows: ReturnType<typeof onboardingTaskRows>;
  canSubmit: boolean;
  companyName: string;
  locale: Locale;
  backTo: string;
  successTo: string | null;
  onRetry: () => void;
}) {
  const { state, form, locale } = props,
    text = onboardingScheduleMessages(locale),
    restore = useRestoreFocusTarget();
  return (
    <section
      className="app-report-content app-record-editor"
      aria-busy={["loading", "preparing", "saving"].includes(state.stage)}
    >
      <AppPageHeader
        title={text.new}
        context={props.companyName}
        actions={
          <Link {...restore} to={props.backTo}>
            {text.backToSchedules}
          </Link>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.stage === "preparing" && <AppLoading label={text.preparing} />}
      {state.stage === "saving" && <AppLoading label={text.saving} />}
      {["editing", "reviewing", "conflict", "unavailable"].includes(state.stage) && (
        <div>
          <AppButton appearance="subtle" icon={<ArrowClockwise20Regular />} onClick={form.reload}>
            {text.reload}
          </AppButton>
        </div>
      )}
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
            {text.scheduled}{" "}
            {props.successTo && (
              <Link {...restore} to={props.successTo}>
                {text.view}
              </Link>
            )}
          </MessageBarBody>
        </MessageBar>
      )}
      {state.employee && ["editing", "preparing"].includes(state.stage) && (
        <form className="app-editor-form" aria-label={text.new} onSubmit={form.prepare} noValidate>
          <AppPropertyList
            title={text.employee}
            items={[
              { label: text.employee, value: state.employee.legalName },
              { label: text.employeeNumber, value: state.employee.employeeNumber },
              { label: text.firstDay, value: state.employee.terms.startDate },
            ]}
          />
          <div className="app-editor-fields">
            <div className="app-form">
              <AppTextField label={text.template} value={form.template?.name ?? ""} readOnly />
              <AppButton
                {...restore}
                disabled={state.stage !== "editing"}
                onClick={form.chooseTemplate}
              >
                {text.chooseTemplate}
              </AppButton>
            </div>
            <AppTextField
              label={text.startsOn}
              {...form.startsOn}
              type="date"
              min="1900-01-01"
              max={state.employee.terms.startDate}
              required
              readOnly={state.stage !== "editing"}
            />
          </div>
          <div className="app-form-actions">
            <AppButton
              appearance="primary"
              type="submit"
              disabled={state.stage !== "editing" || form.template === null}
            >
              {text.reviewAction}
            </AppButton>
          </div>
        </form>
      )}
      {props.view && (
        <>
          <AppPropertyList title={text.review} items={props.view.properties} />
          {props.view.issues.map((issue) => (
            <AppFailure key={issue.code} failure={issue} locale={locale} />
          ))}
          <form
            className="app-editor-form"
            aria-label={text.review}
            onSubmit={form.submit}
            noValidate
          >
            <AppResourceTable
              title={text.checklist}
              columns={[
                { id: "task", label: text.task },
                { id: "required", label: text.required },
                { id: "due", label: text.dueDate },
                { id: "assignee", label: text.assignee },
              ]}
              rows={props.rows}
              {...(state.stage === "reviewing" && props.canSubmit
                ? { action: { label: text.assign, columnId: "task", onOpen: form.chooseAssignee } }
                : {})}
            />
            {props.canSubmit && (
              <AppTextArea
                label={text.reason}
                {...form.reason}
                rows={3}
                maxLength={1000}
                required
                readOnly={state.stage !== "reviewing"}
              />
            )}
            {state.stage === "reviewing" && props.canSubmit && (
              <>
                <p className="app-muted">{text.createNote}</p>
                <div className="app-form-actions">
                  <AppButton appearance="primary" type="submit">
                    {text.new}
                  </AppButton>
                  <AppButton appearance="subtle" onClick={form.revise}>
                    {text.changeSelection}
                  </AppButton>
                </div>
              </>
            )}
            {(state.stage === "conflict" || (state.stage === "reviewing" && !props.canSubmit)) && (
              <div>
                <AppButton appearance="subtle" onClick={form.revise}>
                  {text.changeSelection}
                </AppButton>
              </div>
            )}
          </form>
        </>
      )}
    </section>
  );
}
