import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useAccountInvitationForm } from "../controllers/use-account-invitation-form";
import { accountInvitationMessages } from "../i18n/account-invitation-messages";
import { managedAccountMessages } from "../i18n/managed-account-messages";
import type { AccountInvitationState } from "../models/account-invitation-state";
export function AccountInvitationPage({
  state,
  form,
  locale,
  resend,
  backTo,
  savedTo,
  onRetry,
}: {
  state: AccountInvitationState;
  form: ReturnType<typeof useAccountInvitationForm>;
  locale: Locale;
  resend: boolean;
  backTo: string;
  savedTo: string | null;
  onRetry: () => void;
}) {
  const text = accountInvitationMessages(locale);
  const accounts = managedAccountMessages(locale);
  return (
    <section
      className="app-report-content app-record-editor app-account-invitation"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={resend ? text.resend : text.invite}
        context={accounts.title}
        actions={<Link to={backTo}>{resend ? accounts.backToAccount : accounts.back}</Link>}
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
            {text.queued} {savedTo && <Link to={savedTo}>{accounts.view}</Link>}
          </MessageBarBody>
        </MessageBar>
      )}
      {["editing", "saving", "unconfirmed", "conflict"].includes(state.stage) && (
        <>
          <p>{resend ? text.resendHint : text.hint}</p>
          {state.review && (
            <p>
              {accounts.basedOn}: {state.review.account.version}
            </p>
          )}
          <form className="app-editor-form" onSubmit={form.submit} noValidate>
            <div className="app-editor-fields">
              <AppTextField
                label={text.name}
                {...form.displayName}
                maxLength={200}
                autoComplete="off"
                required
                readOnly={!form.editable}
                error={
                  form.nameInvalid || state.failure?.fields.displayName ? text.invalid : undefined
                }
              />
              <AppTextField
                label={text.email}
                {...form.email}
                type="email"
                autoComplete="off"
                maxLength={254}
                required
                readOnly={resend || !form.editable}
                error={form.emailInvalid || state.failure?.fields.email ? text.invalid : undefined}
              />
            </div>
            <AppTextArea
              label={text.reason}
              {...form.reason}
              maxLength={1000}
              required
              readOnly={!form.editable}
              error={form.reasonInvalid || state.failure?.fields.reason ? text.invalid : undefined}
            />
            <div className="app-form-actions">
              <AppButton type="submit" appearance="primary" disabled={!form.editable}>
                {text.send}
              </AppButton>
              {state.stage === "conflict" && (
                <AppButton onClick={form.refresh}>{text.reload}</AppButton>
              )}
            </div>
          </form>
        </>
      )}
      {state.stage === "saving" && <AppLoading label={text.sending} />}
    </section>
  );
}
