import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useOffboardingForm } from "../controllers/use-offboarding-form";
import { lifecycleCaseMessages } from "../i18n/lifecycle-case-messages";
import { lifecycleMessages } from "../i18n/lifecycle-messages";
import { offboardingMessages } from "../i18n/offboarding-messages";
import type { OffboardingState } from "../models/offboarding-state";
import type { offboardingView } from "../models/offboarding-view";

export function OffboardingPage({
  state,
  form,
  view,
  companyName,
  locale,
  backTo,
  onRetry,
}: {
  state: OffboardingState;
  form: ReturnType<typeof useOffboardingForm>;
  view: ReturnType<typeof offboardingView> | null;
  companyName: string;
  locale: Locale;
  backTo: string;
  onRetry: () => void;
}) {
  const text = offboardingMessages(locale);
  const shared = lifecycleCaseMessages(locale);
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
              {lifecycleMessages(locale).operation}: {state.operationId}
            </div>
            <AppButton onClick={onRetry}>{text.retry}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "completed" && (
        <MessageBar intent="success" role="status">
          <MessageBarBody>
            {text.completed} <Link to={backTo}>{text.open}</Link>
          </MessageBarBody>
        </MessageBar>
      )}
      {view && state.stage !== "completed" && (
        <>
          <AppPropertyList title={shared.details} items={view.properties} />
          <AppResourceTable
            title={shared.tasks}
            columns={[
              { id: "task", label: shared.task },
              { id: "status", label: shared.status },
              { id: "required", label: shared.requirement },
              { id: "due", label: shared.dueDate },
            ]}
            rows={view.tasks}
          />
          <AppPropertyList title={text.review} items={view.employment} />
          <form className="app-editor-form" onSubmit={form.submit} noValidate>
            <div>
              <p>{text.employmentEffect}</p>
              <p>{text.accessEffect}</p>
            </div>
            {!state.failure && <AppFailure failure={form.blocker} locale={locale} />}
            <AppTextArea
              label={shared.reason}
              {...form.reason}
              required
              maxLength={1000}
              resize="vertical"
              readOnly={!form.editable}
            />
            {state.stage === "submitting" && <AppLoading label={text.completing} />}
            <div className="app-form-actions">
              <AppButton appearance="primary" type="submit" disabled={!form.editable}>
                {text.complete}
              </AppButton>
              {(state.stage === "reviewing" || state.stage === "conflict") && (
                <AppButton {...restore} onClick={form.refresh}>
                  {text.reload}
                </AppButton>
              )}
            </div>
          </form>
        </>
      )}
    </section>
  );
}
