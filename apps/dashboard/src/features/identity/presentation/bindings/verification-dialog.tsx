import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppDialog } from "../../../../core/presentation/components/app-dialog";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { IdentityController } from "../controllers/identity-controller";
import { useMfaForm } from "../controllers/use-mfa-form";
import type { IdentityState } from "../models/identity-state";
import { MfaChallengeContent } from "../pages/mfa-challenge-content";
import { MfaRecoveryContent } from "../pages/mfa-recovery-content";

export function VerificationDialog({
  identity,
  state,
  locale,
}: {
  identity: IdentityController;
  state: IdentityState;
  locale: Locale;
}) {
  const form = useMfaForm(identity, state.stage);
  const text = messages(locale);
  return (
    <AppDialog
      open={
        state.workspace !== null && (state.verification !== null || state.stage === "recoveryCodes")
      }
      title={state.stage === "recoveryCodes" ? text.recoveryCodes : text.verification}
      busy={state.busy}
      onDismiss={state.verification === "requested" ? identity.cancelVerification : undefined}
    >
      {(state.stage === "challenge" || state.stage === "loading") && (
        <MfaChallengeContent state={state} actions={identity} form={form} locale={locale} />
      )}
      {state.stage === "recoveryCodes" && (
        <MfaRecoveryContent
          codes={state.recoveryCodes}
          onSaved={identity.acknowledgeRecoveryCodes}
          locale={locale}
        />
      )}
      {state.stage === "loading" && <AppLoading label={text.loading} />}
      {state.stage === "unavailable" && (
        <div className="app-form">
          <AppFailure failure={state.failure} locale={locale} />
          <AppButton onClick={identity.refreshSession}>{text.retry}</AppButton>
          <AppButton appearance="transparent" onClick={identity.signOut}>
            {text.signOut}
          </AppButton>
        </div>
      )}
    </AppDialog>
  );
}
