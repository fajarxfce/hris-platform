import { Text, Title2 } from "@fluentui/react-components";
import { Eye20Regular, EyeOff20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { IdentityController } from "../controllers/identity-controller";
import type { AuthForms } from "../controllers/use-auth-forms";
import type { IdentityState } from "../models/identity-state";

type Props = {
  state: IdentityState;
  actions: IdentityController;
  forms: AuthForms;
  locale: Locale;
};

export function AuthPage({ state, actions, forms, locale }: Props) {
  const text = messages(locale);
  return (
    <main className="app-auth-layout">
      <section className="app-auth-panel" aria-label={text.product}>
        <span className="app-auth-brand">{text.product}</span>
        {state.stage === "loading" && <AppLoading label={text.loading} />}
        {state.stage === "signedOut" && (
          <>
            <Title2 as="h1">{text.signIn}</Title2>
            <AppFailure failure={state.failure} locale={locale} />
            <form className="app-form" onSubmit={forms.submitSignIn}>
              <AppTextField
                label={text.email}
                {...forms.email}
                type="email"
                autoComplete="username"
                maxLength={254}
                required
                disabled={state.busy}
              />
              <AppTextField
                label={text.password}
                {...forms.password}
                type={forms.passwordVisible ? "text" : "password"}
                autoComplete="current-password"
                maxLength={1024}
                required
                disabled={state.busy}
                contentAfter={
                  <AppButton
                    appearance="transparent"
                    size="small"
                    icon={forms.passwordVisible ? <EyeOff20Regular /> : <Eye20Regular />}
                    aria-label={forms.passwordVisible ? text.hidePassword : text.showPassword}
                    onClick={forms.togglePassword}
                  />
                }
              />
              <AppButton type="submit" appearance="primary" disabled={state.busy}>
                {text.signIn}
              </AppButton>
            </form>
            {state.providers.length > 0 && (
              <div className="app-sso-links">
                {state.providers.map((provider) => (
                  <a key={provider.id} href={provider.authorizationPath}>
                    {provider.name}
                  </a>
                ))}
              </div>
            )}
          </>
        )}
        {state.stage === "challenge" && (
          <>
            <Title2 as="h1">{text.verification}</Title2>
            <AppFailure failure={state.failure} locale={locale} />
            {!state.session?.account.mfaConfigured && (
              <>
                <Text>{text.setupDescription}</Text>
                <AppButton
                  onClick={actions.beginEnrollment}
                  disabled={state.busy || !state.session?.assurance.setupAvailable}
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
              <form className="app-form" onSubmit={forms.submitChallenge}>
                <AppTextField
                  label={forms.recovery ? text.recoveryCode : text.authenticatorCode}
                  {...forms.code}
                  autoComplete="one-time-code"
                  inputMode={forms.recovery ? "text" : "numeric"}
                  maxLength={forms.recovery ? 100 : 6}
                  required
                  disabled={state.busy}
                />
                <AppButton type="submit" appearance="primary" disabled={state.busy}>
                  {text.verify}
                </AppButton>
                {state.session?.account.mfaConfigured && (
                  <AppButton
                    appearance="transparent"
                    disabled={state.busy}
                    onClick={forms.toggleRecovery}
                  >
                    {forms.recovery ? text.useAuthenticator : text.useRecoveryCode}
                  </AppButton>
                )}
              </form>
            )}
            <AppButton appearance="transparent" onClick={actions.signOut}>
              {text.signOut}
            </AppButton>
          </>
        )}
        {state.stage === "recoveryCodes" && (
          <>
            <Title2 as="h1">{text.recoveryCodes}</Title2>
            <Text>{text.recoveryDescription}</Text>
            <ul className="app-recovery-codes">
              {state.recoveryCodes.map((code) => (
                <li key={code}>
                  <code>{code}</code>
                </li>
              ))}
            </ul>
            <AppButton appearance="primary" onClick={actions.acknowledgeRecoveryCodes}>
              {text.recoverySaved}
            </AppButton>
          </>
        )}
        {state.stage === "unavailable" && (
          <>
            <Title2 as="h1">{text.sessionUnavailable}</Title2>
            <AppFailure failure={state.failure} locale={locale} />
            <AppButton appearance="primary" onClick={actions.refreshSession}>
              {text.retry}
            </AppButton>
            <AppButton appearance="transparent" onClick={actions.signOut}>
              {text.signOut}
            </AppButton>
          </>
        )}
      </section>
    </main>
  );
}
