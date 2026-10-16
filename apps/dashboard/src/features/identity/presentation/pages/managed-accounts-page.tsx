import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useManagedAccountSearch } from "../controllers/use-managed-account-search";
import { accountInvitationMessages } from "../i18n/account-invitation-messages";
import { managedAccountMessages } from "../i18n/managed-account-messages";
import type { managedAccountRows } from "../models/managed-account-view";
import type { ManagedAccountsState } from "../models/managed-accounts-state";
import { AccountSearchBar } from "../widgets/account-search-bar";

export function ManagedAccountsPage({
  state,
  rows,
  search,
  locale,
  firstPage,
  hasQuery,
  onRefresh,
  onFirst,
  onNext,
  onOpen,
  onReset,
  inviteTo,
}: {
  state: ManagedAccountsState;
  rows: ReturnType<typeof managedAccountRows>;
  search: ReturnType<typeof useManagedAccountSearch>;
  locale: Locale;
  firstPage: boolean;
  hasQuery: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
  onReset: () => void;
  inviteTo: string;
}) {
  const text = managedAccountMessages(locale);
  return (
    <section
      className="app-report-content app-platform-account"
      aria-busy={state.stage === "loading"}
    >
      <AppPageHeader
        title={text.title}
        context={text.scope}
        actions={
          <>
            <Link to={inviteTo}>{accountInvitationMessages(locale).invite}</Link>
            <AppButton
              appearance="subtle"
              icon={<ArrowClockwise20Regular />}
              aria-label={messages(locale).refresh}
              disabled={state.stage === "loading"}
              onClick={onRefresh}
            />
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      <AccountSearchBar search={search} locale={locale} hasQuery={hasQuery} onReset={onReset} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.page &&
        (rows.length === 0 ? (
          <p role="status">{text.empty}</p>
        ) : (
          <AppResourceTable
            title={text.directory}
            rows={rows}
            columns={[
              { id: "name", label: text.name },
              { id: "email", label: text.email },
              { id: "status", label: text.status },
              { id: "mfa", label: text.mfa },
            ]}
            action={{ label: text.view, onOpen, columnId: "name" }}
          />
        ))}
      <fieldset className="app-pagination" aria-label={text.pages}>
        <AppButton disabled={firstPage || state.stage === "loading"} onClick={onFirst}>
          {text.first}
        </AppButton>
        <AppButton disabled={!state.page?.nextCursor || state.stage === "loading"} onClick={onNext}>
          {text.next}
        </AppButton>
      </fieldset>
    </section>
  );
}
