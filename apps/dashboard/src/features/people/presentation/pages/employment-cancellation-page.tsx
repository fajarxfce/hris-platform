import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useEmploymentCancellationForm } from "../controllers/use-employment-cancellation-form";
import { employeeCreationMessages } from "../i18n/employee-creation-messages";
import { employmentCancellationMessages } from "../i18n/employment-cancellation-messages";
import { peopleMessages } from "../i18n/people-messages";
import type { EmploymentCancellationState } from "../models/employment-cancellation-state";
import type { employmentRevisionView } from "../models/employment-history-view";

export function EmploymentCancellationPage({
  state,
  form,
  revision,
  companyName,
  locale,
  backTo,
  onRetry,
}: {
  state: EmploymentCancellationState;
  form: ReturnType<typeof useEmploymentCancellationForm>;
  revision: ReturnType<typeof employmentRevisionView> | null;
  companyName: string;
  locale: Locale;
  backTo: string;
  onRetry: () => void;
}) {
  const text = employmentCancellationMessages(locale);
  const people = peopleMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <section
      className="app-report-content"
      aria-busy={state.stage === "loading" || state.stage === "submitting"}
    >
      <AppPageHeader
        title={text.title}
        context={companyName}
        actions={
          <Link {...restore} to={backTo}>
            {text.back}
          </Link>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.stage === "unavailable" && (
        <AppButton onClick={form.refresh}>{messages(locale).retry}</AppButton>
      )}
      {state.stage === "unconfirmed" && (
        <MessageBar intent="warning" role="status">
          <MessageBarBody>
            {text.unconfirmed}
            <div className="app-reference">
              {employeeCreationMessages(locale).operation}: {state.operationId}
            </div>
            <AppButton onClick={onRetry}>{text.retry}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "cancelled" && (
        <MessageBar intent="success" role="status">
          <MessageBarBody>
            {text.cancelled}{" "}
            <Link {...restore} to={backTo}>
              {text.history}
            </Link>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.details && revision && (
        <>
          <p>
            {text.companyDate}: {state.details.companyDate} · {people.version}:{" "}
            {state.details.version}
          </p>
          <AppPropertyList title={people.revision} items={revision} />
          {state.stage === "reviewing" && !state.details.canCancel && (
            <MessageBar intent="info" role="status">
              <MessageBarBody>{text.unavailable}</MessageBarBody>
            </MessageBar>
          )}
          {state.details.canCancel && (
            <form className="app-editor-form" onSubmit={form.submit} noValidate>
              <p>{text.notice}</p>
              <AppTextArea
                label={text.reason}
                {...form.reason}
                required
                maxLength={1000}
                resize="vertical"
                readOnly={!form.editable}
              />
              {state.stage === "submitting" && <AppLoading label={text.cancelling} />}
              <div className="app-form-actions">
                <AppButton appearance="primary" type="submit" disabled={!form.editable}>
                  {text.cancel}
                </AppButton>
                {(state.stage === "reviewing" || state.stage === "conflict") && (
                  <AppButton {...restore} onClick={form.refresh}>
                    {text.reload}
                  </AppButton>
                )}
              </div>
            </form>
          )}
        </>
      )}
    </section>
  );
}
