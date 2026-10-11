import { useMemo } from "react";
import type { CompanyId } from "../core/domain/identifiers";
import { AppButton } from "../core/presentation/components/app-button";
import { AppFailure } from "../core/presentation/components/app-failure";
import { AppLeaveDialog } from "../core/presentation/components/app-leave-dialog";
import { AppLoading } from "../core/presentation/components/app-loading";
import { AppPortalShell } from "../core/presentation/components/app-portal-shell";
import { AppWorkspaceSurface } from "../core/presentation/components/app-workspace-surface";
import { WorkspaceSessionContext } from "../core/presentation/contracts/workspace-session";
import { type Locale, messages } from "../core/presentation/i18n/messages";
import { WorkspaceNavigationContext } from "../core/presentation/navigation/use-navigation-protection";
import type { IdentityState } from "../features/identity/presentation/models/identity-state";
import type { ApplicationDependencies } from "./application-dependencies";
import { portalNavigation } from "./routing/portal-navigation";
import { PortalRoutes } from "./routing/portal-routes";
import { useWorkspaceNavigation } from "./routing/use-workspace-navigation";

/** Keyed by the authorized workspace: revocation discards navigation decisions with the form. */
export function PortalWorkspace(
  props: ApplicationDependencies & {
    state: IdentityState;
    locale: Locale;
    dark: boolean;
    collapsed: boolean;
    onLocaleChanged: (locale: Locale) => void;
    onThemeChanged: () => void;
    onNavigationToggled: () => void;
  },
) {
  const { state, identity, locale } = props;
  const navigation = useWorkspaceNavigation(state.workspace !== null && state.stage !== "ready");
  const sessionActions = useMemo(
    () => ({ verifyAccount: identity.requestVerification, revalidate: identity.refreshSession }),
    [identity],
  );
  if (!state.session) return null;
  return (
    <>
      <AppPortalShell
        locale={locale}
        dark={props.dark}
        collapsed={props.collapsed}
        accountName={state.session.account.displayName}
        companies={state.workspace?.companies ?? state.session.companies}
        companyId={state.companyId}
        companySelectionDisabled={state.workspace !== null && state.stage !== "ready"}
        onCompanySelected={(company) => {
          if (company !== state.companyId)
            navigation.controller.request(() => {
              void identity.selectCompany(company as CompanyId);
            });
        }}
        onLocaleChanged={props.onLocaleChanged}
        onThemeChanged={props.onThemeChanged}
        onNavigationToggled={props.onNavigationToggled}
        onSignOut={() => {
          if (state.stage !== "ready") {
            void identity.signOut();
            return;
          }
          navigation.controller.request(() => {
            void identity.signOut();
          });
        }}
        onVerifyAccount={
          state.session.account.mfaConfigured ? identity.requestVerification : undefined
        }
        verificationDisabled={state.busy || state.stage !== "ready"}
        navigation={portalNavigation(state.workspace?.access ?? state.access, locale)}
      >
        {state.workspace && (
          <WorkspaceNavigationContext value={navigation.controller}>
            <WorkspaceSessionContext value={sessionActions}>
              <AppWorkspaceSurface visible={state.stage === "ready"}>
                <PortalRoutes
                  clientBuild={props.clientBuild}
                  accountId={state.workspace.accountId}
                  identityAdministration={props.identityAdministration}
                  companies={state.workspace.companies}
                  access={state.workspace.access}
                  reporting={props.reporting}
                  administration={props.administration}
                  approvals={props.approvals}
                  communications={props.communications}
                  jobs={props.jobs}
                  leave={props.leave}
                  organization={props.organization}
                  people={props.people}
                  lifecycle={props.lifecycle}
                  company={state.workspace.company}
                  locale={locale}
                  nextIdentifier={props.nextIdentifier}
                />
              </AppWorkspaceSurface>
            </WorkspaceSessionContext>
          </WorkspaceNavigationContext>
        )}
        {state.stage === "loading" && state.verification === null && (
          <AppLoading label={messages(locale).loading} />
        )}
        {state.stage === "unavailable" && (!state.workspace || state.verification === null) && (
          <div className="app-form">
            <AppFailure failure={state.failure} locale={locale} />
            <AppButton onClick={identity.refreshSession}>{messages(locale).retry}</AppButton>
          </div>
        )}
      </AppPortalShell>
      <AppLeaveDialog
        state={navigation.state}
        locale={locale}
        onLeave={navigation.controller.leave}
        onStay={navigation.controller.stay}
      />
    </>
  );
}
