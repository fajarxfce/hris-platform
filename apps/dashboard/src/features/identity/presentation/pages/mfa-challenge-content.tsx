import { Text } from "@fluentui/react-components";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { IdentityController } from "../controllers/identity-controller";
import type { MfaForm } from "../controllers/use-mfa-form";
import type { IdentityState } from "../models/identity-state";

export function MfaChallengeContent({
  state,
  actions,
  form,
  locale,
}: {
  state: IdentityState;
  actions: IdentityController;
  form: MfaForm;
  locale: Locale;
}) {
  const text = messages(locale);
  return (
    <div className="app-form">
      <AppFailure failure={state.failure} locale={locale} />
      {state.failure &&
        [
          "request_timeout",
          "connection_unavailable",
          "request_failed",
          "invalid_response",
        ].includes(state.failure.code) && (
          <AppButton onClick={actions.refreshSession} disabledFocusable={state.busy}>
            {text.checkSession}
          </AppButton>
        )}
      {!state.session?.account.mfaConfigured && (
        <>
          <Text>{text.setupDescription}</Text>
          <AppButton
            onClick={actions.beginEnrollment}
            disabledFocusable={state.busy || !state.session?.assurance.setupAvailable}
          >
            {text.setupAuthenticator}
          </AppButton>
          {state.enrollment && (
            <div className="app-enrollment-key">
              <Text size={200}>{text.enrollmentKey}</Text>
              <code>{state.enrollment.secret}</code>
            </div>
          )}
        </>
      )}
      {(state.session?.account.mfaConfigured || state.enrollment) && (
        <form className="app-form" onSubmit={form.submitChallenge}>
          <AppTextField
            label={form.recovery ? text.recoveryCode : text.authenticatorCode}
            {...form.code}
            autoComplete="one-time-code"
            inputMode={form.recovery ? "text" : "numeric"}
            maxLength={form.recovery ? 100 : 6}
            required
            readOnly={state.busy}
          />
          <AppButton type="submit" appearance="primary" disabledFocusable={state.busy}>
            {text.verify}
          </AppButton>
          {state.session?.account.mfaConfigured && (
            <AppButton
              appearance="transparent"
              disabledFocusable={state.busy}
              onClick={form.toggleRecovery}
            >
              {form.recovery ? text.useAuthenticator : text.useRecoveryCode}
            </AppButton>
          )}
        </form>
      )}
      {state.verification === "requested" && (
        <AppButton onClick={actions.cancelVerification} disabledFocusable={state.busy}>
          {text.cancel}
        </AppButton>
      )}
      <AppButton appearance="transparent" onClick={actions.signOut}>
        {text.signOut}
      </AppButton>
    </div>
  );
}
