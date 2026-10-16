import { MessageBar, MessageBarBody, Title2 } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { usePasswordRecoveryForm } from "../controllers/use-password-recovery-form";
import { accountCredentialMessages } from "../i18n/account-credential-messages";
import type { PasswordRecoveryState } from "../models/password-recovery-state";
export function PasswordRecoveryPage({
  state,
  form,
  locale,
}: {
  state: PasswordRecoveryState;
  form: ReturnType<typeof usePasswordRecoveryForm>;
  locale: Locale;
}) {
  const text = accountCredentialMessages(locale);
  return (
    <main className="app-auth-layout">
      <section
        className="app-auth-panel"
        aria-label={messages(locale).product}
        aria-busy={state.stage === "sending"}
      >
        <span className="app-auth-brand">{messages(locale).product}</span>
        <Title2 as="h1">{text.recover}</Title2>
        <AppFailure failure={state.failure} locale={locale} />
        {state.stage === "sent" ? (
          <MessageBar layout="multiline" intent="success" role="status">
            <MessageBarBody>{text.requested}</MessageBarBody>
          </MessageBar>
        ) : (
          <form className="app-form" onSubmit={form.submit} noValidate>
            <AppTextField
              label={text.email}
              {...form.email}
              type="email"
              autoComplete="email"
              maxLength={254}
              required
              disabled={state.stage === "sending"}
              error={form.invalid || state.failure?.fields.email ? text.invalidEmail : undefined}
            />
            <AppButton appearance="primary" type="submit" disabled={state.stage === "sending"}>
              {text.request}
            </AppButton>
          </form>
        )}
        {state.stage === "sending" && <AppLoading label={text.requesting} />}
        <Link to="/">{text.back}</Link>
      </section>
    </main>
  );
}
