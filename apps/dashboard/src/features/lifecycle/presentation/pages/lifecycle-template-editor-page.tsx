import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCheckbox } from "../../../../core/presentation/components/app-checkbox";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { lifecycleKinds } from "../../domain/entities/lifecycle-template";
import type { useLifecycleTemplateForm } from "../controllers/use-lifecycle-template-form";
import { lifecycleMessages } from "../i18n/lifecycle-messages";
import type { LifecycleTemplateEditorState } from "../models/lifecycle-template-editor-state";

export function LifecycleTemplateEditorPage({
  state,
  form,
  creating,
  companyName,
  locale,
  backTo,
  savedTo,
  children,
  onRetry,
}: {
  state: LifecycleTemplateEditorState;
  form: ReturnType<typeof useLifecycleTemplateForm>;
  creating: boolean;
  companyName: string;
  locale: Locale;
  backTo: string;
  savedTo: string | null;
  children: ReactNode;
  onRetry: () => void;
}) {
  const text = lifecycleMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <section
      className="app-report-content app-lifecycle-template-editor"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={creating ? text.create : text.edit}
        context={`${companyName} / ${text.title}`}
        actions={
          <Link {...restore} to={backTo}>
            {text.backFromEditor}
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
              {text.operation}: {state.operationId}
            </div>
            <AppButton onClick={onRetry}>{text.retry}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "saved" && (
        <MessageBar intent="success" role="status">
          <MessageBarBody>
            {text.saved} {text.version}: {state.receipt?.version}.{" "}
            {savedTo && (
              <Link {...restore} to={savedTo}>
                {text.view}
              </Link>
            )}
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage !== "loading" && state.stage !== "unavailable" && (
        <>
          {state.template && (
            <p>
              {text.basedOn}: {state.template.version}
            </p>
          )}
          <p>{text.changeHint}</p>
          <form className="app-editor-form" onSubmit={form.submit} noValidate>
            <div className="app-editor-fields">
              <AppTextField
                label={text.code}
                {...form.code}
                maxLength={32}
                required
                readOnly={!creating || !form.editable}
                hint={creating ? text.codeHint : undefined}
              />
              <AppTextField
                label={text.name}
                {...form.name}
                maxLength={120}
                required
                readOnly={!form.editable}
              />
              {creating ? (
                <AppSelect label={text.kind} {...form.kind} disabled={!form.editable}>
                  {lifecycleKinds.map((kind) => (
                    <option key={kind} value={kind}>
                      {text[kind]}
                    </option>
                  ))}
                </AppSelect>
              ) : (
                <AppTextField label={text.kind} value={text[form.kind.value]} readOnly />
              )}
              <AppCheckbox
                label={text.active}
                checked={form.active.value}
                ref={form.active.ref}
                onBlur={form.active.onBlur}
                disabled={!form.editable}
                onChange={(_, data) => form.active.onChange(data.checked === true)}
              />
            </div>
            <p>{text.activeHint}</p>
            <h2>{text.tasks}</h2>
            <p>{text.taskHint}</p>
            {children}
            <div className="app-form-actions">
              <AppButton onClick={form.addTask} disabled={!form.canAdd}>
                {text.addTask}
              </AppButton>
            </div>
            <AppTextField
              label={text.reason}
              {...form.reason}
              maxLength={1000}
              required
              readOnly={!form.editable}
            />
            {state.stage === "saving" && <AppLoading label={text.saving} />}
            <div className="app-form-actions">
              <AppButton appearance="primary" type="submit" disabled={!form.editable}>
                {text.save}
              </AppButton>
              {!creating && (state.stage === "editing" || state.stage === "conflict") && (
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
