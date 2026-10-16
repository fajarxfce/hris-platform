import { Link, Route, Routes } from "react-router-dom";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { messages } from "../../../../core/presentation/i18n/messages";
import { AccountAccessEditorScreen } from "../bindings/account-access-editor-screen";
import { AccountDeviceSessionsScreen } from "../bindings/account-device-sessions-screen";
import { AccountInvitationScreen } from "../bindings/account-invitation-screen";
import { ManagedAccountScreen } from "../bindings/managed-account-screen";
import { ManagedAccountsScreen } from "../bindings/managed-accounts-screen";
import { OidcBindingEditorScreen } from "../bindings/oidc-binding-editor-screen";
import { OidcBindingsScreen } from "../bindings/oidc-bindings-screen";
import type { ManagedAccountScreenProps } from "../contracts/managed-account-screen-props";
import { managedAccountMessages } from "../i18n/managed-account-messages";

export function PlatformAccountRoutes(props: ManagedAccountScreenProps) {
  return (
    <Routes>
      <Route path="/platform/accounts" element={<ManagedAccountsScreen {...props} />} />
      <Route path="/platform/accounts/new" element={<AccountInvitationScreen {...props} />} />
      <Route
        path="/platform/accounts/:managedAccountId/sessions"
        element={<AccountDeviceSessionsScreen {...props} />}
      />
      <Route
        path="/platform/accounts/:managedAccountId/sso"
        element={<OidcBindingsScreen {...props} />}
      />
      <Route
        path="/platform/accounts/:managedAccountId/sso/new"
        element={<OidcBindingEditorScreen {...props} />}
      />
      <Route
        path="/platform/accounts/:managedAccountId/sso/:oidcBindingId"
        element={<OidcBindingEditorScreen {...props} />}
      />
      <Route
        path="/platform/accounts/:managedAccountId/invite"
        element={<AccountInvitationScreen {...props} />}
      />
      <Route
        path="/platform/accounts/:managedAccountId"
        element={<ManagedAccountScreen {...props} />}
      />
      <Route
        path="/platform/accounts/:managedAccountId/edit"
        element={<AccountAccessEditorScreen {...props} />}
      />
      <Route
        path="*"
        element={
          <>
            <AppPageHeader title={messages(props.locale).pageNotFound} />
            <Link to="/platform/accounts">{managedAccountMessages(props.locale).back}</Link>
          </>
        }
      />
    </Routes>
  );
}
