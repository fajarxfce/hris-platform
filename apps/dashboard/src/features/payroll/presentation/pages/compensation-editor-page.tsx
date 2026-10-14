import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useCompensationForm } from "../controllers/use-compensation-form";
import { compensationFieldError, compensationMessages } from "../i18n/compensation-messages";
import type { CompensationEditorState } from "../models/compensation-editor-state";
export function CompensationEditorPage({
  state,
  form,
  companyName,
  locale,
  backTo,
  children,
  onRetry,
}: {
  state: CompensationEditorState;
  form: ReturnType<typeof useCompensationForm>;
  companyName: string;
  locale: Locale;
  backTo: string;
  children: ReactNode;
  onRetry: () => void;
}) {
  const text = compensationMessages(locale),
    restore = useRestoreFocusTarget();
  return (
    <section
      className="app-report-content app-record-editor app-compensation-editor"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={state.context?.employee.name ?? text.title}
        context={`${companyName} / ${text.title}`}
        actions={
          <Link {...restore} to={backTo}>
            {text.backToCompensation}
          </Link>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.stage === "saving" && <AppLoading label={text.saving} />}
      {state.stage === "unavailable" && (
        <AppButton onClick={form.refresh}>{messages(locale).retry}</AppButton>
      )}
      {state.stage === "unconfirmed" && (
        <MessageBar layout="multiline" intent="warning" role="status">
          <MessageBarBody>
            {text.unconfirmed}
            <div className="app-reference">
              {text.operation}: {state.operationId}
            </div>
            <AppButton onClick={onRetry}>{text.retrySave}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "saved" && (
        <MessageBar layout="multiline" intent="success" role="status">
          <MessageBarBody>
            {text.saved} {text.version}: {state.receipt?.version}.{" "}
            <Link {...restore} to={backTo}>
              {text.view}
            </Link>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.context && (
        <>
          <p className="app-muted">
            {text.employeeNumber}: {state.context.employee.employeeNumber}
            {state.context.current && (
              <>
                {" "}
                · {text.basedOn}: {state.context.current.version}
              </>
            )}
          </p>
          <form className="app-editor-form" onSubmit={form.submit} noValidate>
            <div className="app-editor-fields">
              <AppTextField
                label={text.from}
                {...form.effectiveFrom}
                type="month"
                min="2024-01"
                max="2100-12"
                required
                readOnly={!form.editable}
                error={compensationFieldError(state.failure, "effectiveFrom", locale)}
              />
            </div>
            {children}
            <AppTextArea
              label={text.reason}
              {...form.reason}
              maxLength={1000}
              rows={2}
              required
              readOnly={!form.editable}
              error={compensationFieldError(state.failure, "reason", locale)}
            />
            <div className="app-form-actions">
              <AppButton appearance="primary" type="submit" disabled={!form.editable}>
                {text.save}
              </AppButton>
              {state.stage === "conflict" && (
                <AppButton onClick={form.refresh}>{text.reload}</AppButton>
              )}
            </div>
          </form>
        </>
      )}
    </section>
  );
}
