import { FluentProvider, webDarkTheme, webLightTheme } from "@fluentui/react-components";
import { useEffect, useReducer, useSyncExternalStore } from "react";
import { AppPreferences } from "../core/presentation/components/app-preferences";
import type { Locale } from "../core/presentation/i18n/messages";
import { AuthScreen } from "../features/identity/presentation/bindings/auth-screen";
import { VerificationDialog } from "../features/identity/presentation/bindings/verification-dialog";
import type { ApplicationDependencies } from "./application-dependencies";
import { useIdentityLifecycle } from "./lifecycle/use-identity-lifecycle";
import { PortalWorkspace } from "./portal-workspace";

export function Application(props: ApplicationDependencies) {
  const { identity } = props;
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
      (state.workspace !== null ||
        state.stage === "ready" ||
        state.stage === "loading" ||
        state.stage === "unavailable") ? (
        <PortalWorkspace
          key={state.workspace?.revision ?? "pending"}
          {...props}
          state={state}
          locale={locale}
          dark={dark}
          collapsed={collapsed}
          onLocaleChanged={setLocale}
          onThemeChanged={toggleTheme}
          onNavigationToggled={toggleNavigation}
        />
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
      <VerificationDialog identity={identity} state={state} locale={locale} />
    </FluentProvider>
  );
}
