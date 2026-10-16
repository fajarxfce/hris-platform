import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCheckbox } from "../../../../core/presentation/components/app-checkbox";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useRoleTemplateForm } from "../controllers/use-role-template-form";
import { roleTemplateMessages } from "../i18n/role-template-messages";
import type { RoleTemplateEditorState } from "../models/role-template-editor-state";
import { RolePermissions } from "../widgets/role-permissions";
export function RoleTemplateEditorPage({
  state,
  form,
  creating,
  companyName,
  locale,
  backTo,
  savedTo,
  onRetry,
}: {
  state: RoleTemplateEditorState;
  form: ReturnType<typeof useRoleTemplateForm>;
  creating: boolean;
  companyName: string;
  locale: Locale;
  backTo: string;
  savedTo: string | null;
  onRetry: () => void;
}) {
  const text = roleTemplateMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <section
      className="app-report-content app-role-editor"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={creating ? text.create : text.edit}
        context={`${companyName} / ${text.title}`}
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
        <MessageBar layout="multiline" intent="warning" role="status">
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
        <MessageBar layout="multiline" intent="success" role="status">
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
      {state.stage !== "loading" && state.stage !== "unavailable" && state.stage !== "saved" && (
        <>
          <p>{text.assignmentHint}</p>
          {state.template && (
            <p>
              {text.basedOn}: {state.template.version}
            </p>
          )}
          <form className="app-editor-form" onSubmit={form.submit} noValidate>
            <div className="app-editor-fields">
              <AppTextField
                label={text.code}
                {...form.code}
                maxLength={32}
                required
                readOnly={!creating || !form.editable}
                hint={text.codeHint}
                error={state.failure?.fields.code ? text.invalid : undefined}
              />
              <AppTextField
                label={text.name}
                {...form.name}
                maxLength={200}
                required
                readOnly={!form.editable}
                error={state.failure?.fields.name ? text.invalid : undefined}
              />
            </div>
            <AppCheckbox
              label={text.active}
              checked={form.active.value}
              ref={form.active.ref}
              onBlur={form.active.onBlur}
              disabled={!form.editable}
              onChange={(_, data) => form.active.onChange(data.checked === true)}
            />
            <RolePermissions
              title={text.permissions}
              empty={text.none}
              groups={form.groups}
              editable={form.editable}
              onSelect={form.selectPermission}
            />
            {state.failure?.fields.permissions && <p role="alert">{text.invalidPermissions}</p>}
            <AppTextArea
              label={text.reason}
              {...form.reason}
              maxLength={1000}
              required
              readOnly={!form.editable}
              error={state.failure?.fields.reason ? text.invalid : undefined}
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
