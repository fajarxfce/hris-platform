import { useEffect, useLayoutEffect, useMemo, useSyncExternalStore } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { AccountCredentialKind } from "../../domain/entities/account-credential";
import type { AccountCredentialUseCases } from "../contracts/account-credential-use-cases";
import { CredentialConfirmationController } from "../controllers/credential-confirmation-controller";
import { useCredentialPasswordForm } from "../controllers/use-credential-password-form";
import { credentialTokenFromFragment } from "../models/credential-link-route";
import { CredentialConfirmationPage } from "../pages/credential-confirmation-page";
export function CredentialConfirmationScreen({
  actions,
  kind,
  locale,
}: {
  actions: AccountCredentialUseCases;
  kind: AccountCredentialKind;
  locale: Locale;
}) {
  const location = useLocation();
  const navigate = useNavigate();
  const controller = useMemo(
    () => new CredentialConfirmationController(actions, kind),
    [actions, kind],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useLayoutEffect(() => {
    if (location.hash || location.search) {
      controller.capture(location.search ? null : credentialTokenFromFragment(location.hash));
      void navigate(
        { pathname: location.pathname, search: "", hash: "" },
        { replace: true, state: null },
      );
    }
  }, [controller, location.pathname, location.hash, location.search, navigate]);
  useEffect(() => {
    controller.activate();
    return controller.deactivate;
  }, [controller]);
  useEffect(() => {
    if (state.stage === "signedOut") void navigate("/", { replace: true, state: null });
  }, [state.stage, navigate]);
  const form = useCredentialPasswordForm(controller);
  return (
    <CredentialConfirmationPage
      kind={kind}
      state={state}
      form={form}
      locale={locale}
      onContinue={controller.continueToSignIn}
    />
  );
}
