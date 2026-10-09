import { FluentProvider, webDarkTheme, webLightTheme } from "@fluentui/react-components";
import { useEffect, useReducer, useSyncExternalStore } from "react";
import type { CompanyId } from "../core/domain/identifiers";
import { AppButton } from "../core/presentation/components/app-button";
import { AppFailure } from "../core/presentation/components/app-failure";
import { AppLoading } from "../core/presentation/components/app-loading";
import { AppPortalShell } from "../core/presentation/components/app-portal-shell";
import { AppPreferences } from "../core/presentation/components/app-preferences";
import { type Locale, messages } from "../core/presentation/i18n/messages";
import type { AdministrationUseCases } from "../features/administration/presentation/contracts/administration-use-cases";
import { AuthScreen } from "../features/identity/presentation/bindings/auth-screen";
import type { IdentityController } from "../features/identity/presentation/controllers/identity-controller";
import type { JobsUseCases } from "../features/jobs/presentation/contracts/jobs-use-cases";
import type { ReportingUseCases } from "../features/reporting/presentation/contracts/reporting-use-cases";
import { useIdentityLifecycle } from "./lifecycle/use-identity-lifecycle";
import { portalNavigation } from "./routing/portal-navigation";
import { PortalRoutes } from "./routing/portal-routes";

export function Application({
  identity,
  reporting,
  administration,
  jobs,
}: {
  identity: IdentityController;
  reporting: ReportingUseCases;
  administration: AdministrationUseCases;
  jobs: JobsUseCases;
}) {
  const state = useSyncExternalStore(
    identity.subscribe,
    identity.getSnapshot,
    identity.getSnapshot,
  );
  const [locale, setLocale] = useReducer((_previous: Locale, next: Locale) => next, "en");
  const [dark, toggleTheme] = useReducer((previous: boolean) => !previous, false);
  const [collapsed, toggleNavigation] = useReducer((previous: boolean) => !previous, false);
  useIdentityLifecycle(identity);
  useEffect(() => {
    document.documentElement.lang = locale;
  }, [locale]);
  return (
    <FluentProvider
      theme={dark ? webDarkTheme : webLightTheme}
      style={{ colorScheme: dark ? "dark" : "light" }}
    >
      {state.session &&
      (state.stage === "ready" || state.stage === "loading" || state.stage === "unavailable") ? (
        <AppPortalShell
          locale={locale}
          dark={dark}
          collapsed={collapsed}
          accountName={state.session.account.displayName}
          companies={state.session.companies}
          companyId={state.companyId}
          onCompanySelected={(company) => {
            void identity.selectCompany(company as CompanyId);
          }}
          onLocaleChanged={setLocale}
          onThemeChanged={toggleTheme}
          onNavigationToggled={toggleNavigation}
          onSignOut={identity.signOut}
          navigation={portalNavigation(state.access, locale)}
        >
          {state.stage === "ready" ? (
            <PortalRoutes
              accountId={state.session.account.id}
              companies={state.session.companies}
              access={state.access}
              reporting={reporting}
              administration={administration}
              jobs={jobs}
              company={
                state.session.companies.find((company) => company.id === state.companyId) ?? null
              }
              locale={locale}
            />
          ) : state.stage === "loading" ? (
            <AppLoading label={messages(locale).loading} />
          ) : (
            <div className="app-form">
              <AppFailure failure={state.failure} locale={locale} />
              <AppButton onClick={identity.refreshSession}>{messages(locale).retry}</AppButton>
            </div>
          )}
        </AppPortalShell>
      ) : (
        <div className="app-auth-shell">
          <header className="app-auth-preferences">
            <AppPreferences
              locale={locale}
              dark={dark}
              onLocaleChanged={setLocale}
              onThemeChanged={toggleTheme}
            />
          </header>
          <AuthScreen identity={identity} state={state} locale={locale} />
        </div>
      )}
    </FluentProvider>
  );
}
