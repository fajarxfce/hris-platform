import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCheckbox } from "../../../../core/presentation/components/app-checkbox";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useOidcBindingForm } from "../controllers/use-oidc-binding-form";
import { managedAccountMessages } from "../i18n/managed-account-messages";
import { oidcBindingMessages } from "../i18n/oidc-binding-messages";
import type { OidcBindingEditorState } from "../models/oidc-binding-editor-state";
import type { oidcBindingProperties } from "../models/oidc-binding-view";

export function OidcBindingEditorPage({
  state,
  form,
  properties,
  locale,
  creating,
  ownAccount,
  backTo,
  savedTo,
  onRetry,
}: {
  state: OidcBindingEditorState;
  form: ReturnType<typeof useOidcBindingForm>;
  properties: ReturnType<typeof oidcBindingProperties>;
  locale: Locale;
  creating: boolean;
  ownAccount: boolean;
  backTo: string;
  savedTo: string | null;
  onRetry: () => void;
}) {
  const text = oidcBindingMessages(locale);
  const accounts = managedAccountMessages(locale);
  return (
    <section
      className="app-report-content app-record-editor app-oidc-binding"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={creating ? text.create : text.editor}
        context={state.review?.account.displayName ?? text.title}
        actions={<Link to={backTo}>{text.back}</Link>}
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
              {accounts.operation}: {state.operationId}
            </div>
            <AppButton onClick={onRetry}>{text.retry}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "saved" && (
        <MessageBar layout="multiline" intent="success" role="status">
          <MessageBarBody>
            {text.saved}{" "}
            {savedTo ? (
              <Link to={savedTo}>{text.view}</Link>
            ) : (
              <AppButton appearance="subtle" onClick={form.refresh}>
                {text.view}
              </AppButton>
            )}
          </MessageBarBody>
        </MessageBar>
      )}
      {state.review && (
        <>
          <p>{state.review.account.email}</p>
          {state.review.binding && <AppPropertyList title={text.editor} items={properties} />}
          <p>{ownAccount ? text.ownSessionEnds : text.sessionsEnd}</p>
          <p>{creating ? text.identityHint : text.immutable}</p>
          {!state.review.account.active && (
            <MessageBar layout="multiline" intent="warning">
              <MessageBarBody>{text.disabledAccount}</MessageBarBody>
            </MessageBar>
          )}
          <form className="app-editor-form" onSubmit={form.submit} noValidate>
            <div className="app-editor-fields">
              <AppTextField
                label={text.issuer}
                {...form.issuer}
                required
                maxLength={500}
                readOnly={form.identityReadOnly}
                autoComplete="off"
                spellCheck={false}
                error={
                  form.issuerInvalid || state.failure?.fields.issuer ? accounts.invalid : undefined
                }
              />
              <AppTextField
                label={text.subject}
                {...form.subject}
                required
                maxLength={255}
                readOnly={form.identityReadOnly}
                autoComplete="off"
                spellCheck={false}
                hint={creating ? text.subjectHint : undefined}
                error={
                  form.subjectInvalid || state.failure?.fields.subject
                    ? accounts.invalid
                    : undefined
                }
              />
            </div>
            <AppCheckbox
              label={text.enabled}
              checked={form.active.value}
              ref={form.active.ref}
              onBlur={form.active.onBlur}
              disabled={form.activeDisabled}
              onChange={(_, data) => form.active.onChange(data.checked === true)}
            />
            <AppTextArea
              label={accounts.reason}
              {...form.reason}
              required
              maxLength={1000}
              readOnly={!form.editable}
              error={
                form.reasonInvalid || state.failure?.fields.reason ? accounts.invalid : undefined
              }
            />
            <div className="app-form-actions">
              <AppButton appearance="primary" type="submit" disabled={!form.canSave}>
                {text.save}
              </AppButton>
              {state.stage === "conflict" && (
                <AppButton onClick={form.refresh}>{accounts.reload}</AppButton>
              )}
              {form.editable && !form.changed && (
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
