import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCheckbox } from "../../../../core/presentation/components/app-checkbox";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useAccountAccessForm } from "../controllers/use-account-access-form";
import { managedAccountMessages } from "../i18n/managed-account-messages";
import type { AccountAccessEditorState } from "../models/account-access-editor-state";

export function AccountAccessEditorPage({
  state,
  form,
  locale,
  backTo,
  savedTo,
  onRetry,
}: {
  state: AccountAccessEditorState;
  form: ReturnType<typeof useAccountAccessForm>;
  locale: Locale;
  backTo: string;
  savedTo: string | null;
  onRetry: () => void;
}) {
  const text = managedAccountMessages(locale);
  return (
    <section
      className="app-report-content app-platform-account"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={text.editor}
        context={text.title}
        actions={<Link to={backTo}>{text.backToAccount}</Link>}
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
            {text.saved} {savedTo && <Link to={savedTo}>{text.view}</Link>}
          </MessageBarBody>
        </MessageBar>
      )}
      {state.review && (
        <>
          <div className="app-member-summary">
            <h2>{state.review.account.displayName}</h2>
            <p>{state.review.account.email}</p>
            <p>
              {text.basedOn}: {state.review.account.version}
            </p>
          </div>
          <p>{text.sessionsEnd}</p>
          {form.ownAccount && <p>{text.ownAccount}</p>}
          {form.invitationPending && (
            <MessageBar layout="multiline" intent="warning">
              <MessageBarBody>{text.pendingHint}</MessageBarBody>
            </MessageBar>
          )}
          <form className="app-editor-form" onSubmit={form.submit} noValidate>
            <AppCheckbox
              label={text.activeAccount}
              checked={form.active.value}
              ref={form.active.ref}
              onBlur={form.active.onBlur}
              disabled={form.activeDisabled}
              onChange={(_, data) => form.active.onChange(data.checked === true)}
            />
            <fieldset className="app-panel app-account-permissions">
              <legend>{text.permissions}</legend>
              {form.options.map((option) => (
                <AppCheckbox
                  key={option.id}
                  label={option.label}
                  checked={option.selected}
                  disabled={option.disabled}
                  onChange={(_, data) => form.selectPermission(option.id, data.checked === true)}
                />
              ))}
            </fieldset>
            {state.failure?.fields.platformPermissions && <p role="alert">{text.invalid}</p>}
            <AppTextArea
              label={text.reason}
              {...form.reason}
              maxLength={1000}
              required
              readOnly={!form.editable}
              error={form.reasonInvalid || state.failure?.fields.reason ? text.invalid : undefined}
            />
            <div className="app-form-actions">
              <AppButton appearance="primary" type="submit" disabled={!form.canSave}>
                {text.save}
              </AppButton>
              {state.stage === "conflict" && (
                <AppButton onClick={form.refresh}>{text.reload}</AppButton>
              )}
              {form.editable && !form.changedAccess && (
                <span className="app-muted">{text.noChanges}</span>
              )}
            </div>
          </form>
        </>
      )}
      {state.stage === "saving" && <AppLoading label={text.saving} />}
    </section>
  );
}
