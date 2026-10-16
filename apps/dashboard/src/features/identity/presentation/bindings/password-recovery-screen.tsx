import { useEffect, useMemo, useSyncExternalStore } from "react";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { AccountCredentialUseCases } from "../contracts/account-credential-use-cases";
import { PasswordRecoveryController } from "../controllers/password-recovery-controller";
import { usePasswordRecoveryForm } from "../controllers/use-password-recovery-form";
import { PasswordRecoveryPage } from "../pages/password-recovery-page";
export function PasswordRecoveryScreen({
  actions,
  locale,
}: {
  actions: AccountCredentialUseCases;
  locale: Locale;
}) {
  const controller = useMemo(
    () => new PasswordRecoveryController(actions.requestPasswordRecovery),
    [actions],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => {
    controller.activate();
    return controller.deactivate;
  }, [controller]);
  const form = usePasswordRecoveryForm(controller);
  return <PasswordRecoveryPage state={state} form={form} locale={locale} />;
}
