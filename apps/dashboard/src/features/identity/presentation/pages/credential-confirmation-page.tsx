import { MessageBar, MessageBarBody, Title2 } from "@fluentui/react-components";
import { Eye20Regular, EyeOff20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { AccountCredentialKind } from "../../domain/entities/account-credential";
import type { useCredentialPasswordForm } from "../controllers/use-credential-password-form";
import { accountCredentialMessages } from "../i18n/account-credential-messages";
import type { CredentialConfirmationState } from "../models/credential-confirmation-state";
export function CredentialConfirmationPage({
  kind,
  state,
  form,
  locale,
  onContinue,
}: {
  kind: AccountCredentialKind;
  state: CredentialConfirmationState;
  form: ReturnType<typeof useCredentialPasswordForm>;
  locale: Locale;
  onContinue: () => void;
}) {
  const text = accountCredentialMessages(locale);
  const common = messages(locale);
  return (
    <main className="app-auth-layout">
      <section
        className="app-auth-panel"
        aria-label={common.product}
        aria-busy={state.stage === "submitting" || state.leaving}
      >
        <span className="app-auth-brand">{common.product}</span>
        <Title2 as="h1">{kind === "invitation" ? text.setPassword : text.resetPassword}</Title2>
        <AppFailure failure={state.failure} locale={locale} />
        {(state.stage === "editing" || state.stage === "submitting") && (
          <form className="app-form" onSubmit={form.submit} noValidate>
            <AppTextField
              label={text.newPassword}
              {...form.password}
              type={form.visible ? "text" : "password"}
              autoComplete="new-password"
              maxLength={128}
              required
              disabled={state.stage === "submitting"}
              hint={text.length}
              error={
                form.passwordInvalid || state.failure?.fields.password ? text.length : undefined
              }
              contentAfter={
                <AppButton
                  appearance="transparent"
                  size="small"
                  icon={form.visible ? <EyeOff20Regular /> : <Eye20Regular />}
                  aria-label={form.visible ? common.hidePassword : common.showPassword}
                  onClick={form.toggleVisibility}
                />
              }
            />
            <AppTextField
              label={text.confirmation}
              {...form.confirmation}
              type={form.visible ? "text" : "password"}
              autoComplete="new-password"
              maxLength={128}
              required
              disabled={state.stage === "submitting"}
              error={form.confirmationInvalid ? text.mismatch : undefined}
            />
            <AppButton appearance="primary" type="submit" disabled={state.stage === "submitting"}>
              {text.save}
            </AppButton>
          </form>
        )}
        {state.stage === "submitting" && <AppLoading label={text.saving} />}
        {state.stage === "saved" && (
          <MessageBar intent="success" layout="multiline" role="status">
            <MessageBarBody>{text.saved}</MessageBarBody>
          </MessageBar>
        )}
        {state.stage === "unconfirmed" && (
          <MessageBar intent="warning" layout="multiline" role="status">
            <MessageBarBody>{text.unconfirmed}</MessageBarBody>
          </MessageBar>
        )}
        {state.stage === "unavailable" && kind === "invitation" && <p>{text.unavailableHint}</p>}
        {["saved", "unconfirmed", "unavailable"].includes(state.stage) && (
          <div className="app-form">
            <AppButton appearance="primary" disabled={state.leaving} onClick={onContinue}>
              {text.signIn}
            </AppButton>
            <Link to="/auth/recover-password">{text.anotherLink}</Link>
          </div>
        )}
        {state.leaving && <AppLoading label={common.loading} />}
      </section>
    </main>
  );
}
