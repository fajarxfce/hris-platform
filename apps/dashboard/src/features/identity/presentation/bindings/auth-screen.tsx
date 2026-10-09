import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { IdentityController } from "../controllers/identity-controller";
import { useAuthForms } from "../controllers/use-auth-forms";
import type { IdentityState } from "../models/identity-state";
import { AuthPage } from "../pages/auth-page";

export function AuthScreen({
  identity,
  state,
  locale,
}: {
  identity: IdentityController;
  state: IdentityState;
  locale: Locale;
}) {
  const forms = useAuthForms(identity, state.stage);
  return <AuthPage state={state} actions={identity} forms={forms} locale={locale} />;
}
