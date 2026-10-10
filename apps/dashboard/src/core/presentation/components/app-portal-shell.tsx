import { Avatar, Select, useRestoreFocusTarget } from "@fluentui/react-components";
import {
  Navigation20Regular,
  ShieldCheckmark20Regular,
  SignOut20Regular,
} from "@fluentui/react-icons";
import type { ReactNode } from "react";
import { NavLink } from "react-router-dom";
import { type Locale, messages } from "../i18n/messages";
import { AppButton } from "./app-button";
import { AppPreferences } from "./app-preferences";

export type AppNavigationItem = Readonly<{ to: string; label: string; icon: ReactNode }>;

type Props = {
  locale: Locale;
  dark: boolean;
  collapsed: boolean;
  accountName: string;
  companies: readonly { id: string; name: string }[];
  companyId: string | null;
  onCompanySelected: (id: string) => void;
  onLocaleChanged: (locale: Locale) => void;
  onThemeChanged: () => void;
  onNavigationToggled: () => void;
  onSignOut: () => void;
  onVerifyAccount?: (() => void) | undefined;
  verificationDisabled?: boolean;
  navigation: readonly AppNavigationItem[];
  children: ReactNode;
};

export function AppPortalShell(props: Props) {
  const restoreFocus = useRestoreFocusTarget();
  const text = messages(props.locale);
  return (
    <div className={`app-portal${props.collapsed ? " app-portal-collapsed" : ""}`}>
      <a href="#main-content" className="app-skip-link">
        {text.skipToContent}
      </a>
      <header className="app-topbar">
        <AppButton
          appearance="subtle"
          icon={<Navigation20Regular />}
          aria-label={text.navigation}
          aria-expanded={!props.collapsed}
          aria-controls="app-main-navigation"
          onClick={props.onNavigationToggled}
        />
        <span className="app-wordmark">{text.product}</span>
        <Select
          className="app-company-select"
          aria-label={text.company}
          value={props.companyId ?? ""}
          onChange={(_, data) => props.onCompanySelected(data.value)}
          disabled={props.companies.length === 0}
        >
          {props.companies.length === 0 && <option value="">{text.noCompanies}</option>}
          {props.companies.map((company) => (
            <option key={company.id} value={company.id}>
              {company.name}
            </option>
          ))}
        </Select>
        <div className="app-topbar-spacer" />
        <AppPreferences
          locale={props.locale}
          dark={props.dark}
          onLocaleChanged={props.onLocaleChanged}
          onThemeChanged={props.onThemeChanged}
        />
        <Avatar className="app-account-avatar" name={props.accountName} size={28} />
        <span className="app-account-name">{props.accountName}</span>
        {props.onVerifyAccount && (
          <AppButton
            {...restoreFocus}
            appearance="subtle"
            icon={<ShieldCheckmark20Regular />}
            aria-label={text.verification}
            onClick={props.onVerifyAccount}
            disabled={props.verificationDisabled ?? false}
          />
        )}
        <AppButton
          appearance="subtle"
          icon={<SignOut20Regular />}
          aria-label={text.signOut}
          onClick={props.onSignOut}
        />
      </header>
      <aside className="app-sidebar">
        <nav id="app-main-navigation" aria-label={text.navigation}>
          {props.navigation.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end
              className={({ isActive }) =>
                `app-nav-item${isActive ? " app-nav-item-selected" : ""}`
              }
              title={item.label}
              aria-label={item.label}
            >
              {item.icon}
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>
      </aside>
      <main id="main-content" tabIndex={-1} className="app-workspace">
        {props.children}
      </main>
    </div>
  );
}
