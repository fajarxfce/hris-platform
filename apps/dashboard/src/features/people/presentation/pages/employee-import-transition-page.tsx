import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCheckbox } from "../../../../core/presentation/components/app-checkbox";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { EmployeeImportAction } from "../../domain/entities/employee-import-change";
import type { useEmployeeImportTransitionForm } from "../controllers/use-employee-import-transition-form";
import { employeeCreationMessages } from "../i18n/employee-creation-messages";
import { employeeImportMessages } from "../i18n/employee-import-messages";
import { employeeImportTransitionMessages } from "../i18n/employee-import-transition-messages";
import type { EmployeeImportTransitionState } from "../models/employee-import-transition-state";
import type { employeeImportView } from "../models/employee-import-view";

export function EmployeeImportTransitionPage({
  state,
  form,
  view,
  action,
  companyName,
  locale,
  backTo,
  onRetry,
}: {
  state: EmployeeImportTransitionState;
  form: ReturnType<typeof useEmployeeImportTransitionForm>;
  view: ReturnType<typeof employeeImportView> | null;
  action: EmployeeImportAction;
  companyName: string;
  locale: Locale;
  backTo: string;
  onRetry: () => void;
}) {
  const text = employeeImportTransitionMessages(locale);
  const shared = employeeImportMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <section
      className="app-report-content"
      aria-busy={state.stage === "loading" || state.stage === "submitting"}
    >
      <AppPageHeader
        title={text[action]}
        context={`${companyName} / ${shared.title}`}
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
      {state.stage === "saved" && (
        <MessageBar intent="success" role="status">
          <MessageBarBody>
            {text[`${action}Saved`]} <Link to={backTo}>{text.open}</Link>
          </MessageBarBody>
        </MessageBar>
      )}
      {view && (
        <>
          <AppPropertyList title={text.review} items={view.properties} />
          <AppPropertyList title={shared.rows} items={view.counts} />
          <form className="app-editor-form" onSubmit={form.submit} noValidate>
            <p>{text[`${action}Note`]}</p>
            {!state.failure && <AppFailure failure={form.blocker} locale={locale} />}
            {action === "apply" && state.review?.counts.INVALID !== 0 && (
              <AppCheckbox
                name={form.partial.name}
                ref={form.partial.ref}
                onBlur={form.partial.onBlur}
                checked={form.partial.value}
                onChange={(_, data) => form.partial.onChange(data.checked === true)}
                disabled={!form.editable}
                label={text.allowPartial}
              />
            )}
            <AppTextArea
              label={shared.reason}
              {...form.reason}
              required
              maxLength={1000}
              resize="vertical"
              readOnly={!form.editable}
            />
            {state.stage === "submitting" && <AppLoading label={messages(locale).loading} />}
            <div className="app-form-actions">
              <AppButton appearance="primary" type="submit" disabled={!form.ready}>
                {text[action]}
              </AppButton>
              {(state.stage === "reviewing" || state.stage === "conflict") && (
                <AppButton {...restore} onClick={form.refresh}>
                  {text.refresh}
                </AppButton>
              )}
            </div>
          </form>
        </>
      )}
    </section>
  );
}
