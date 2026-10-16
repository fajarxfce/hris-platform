import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useManagedAccountSearch } from "../controllers/use-managed-account-search";
import { companyMembershipCreationMessages } from "../i18n/company-membership-creation-messages";
import { managedAccountMessages } from "../i18n/managed-account-messages";
import type { companyAccountCandidateRows } from "../models/company-account-candidate-view";
import type { ManagedAccountsState } from "../models/managed-accounts-state";
import { AccountSearchBar } from "../widgets/account-search-bar";

export function CompanyAccountPickerPage({
  state,
  rows,
  search,
  locale,
  companyName,
  backTo,
  firstPage,
  hasQuery,
  onRefresh,
  onFirst,
  onNext,
  onSelect,
  onReset,
}: {
  state: ManagedAccountsState;
  rows: ReturnType<typeof companyAccountCandidateRows>;
  search: ReturnType<typeof useManagedAccountSearch>;
  locale: Locale;
  companyName: string;
  backTo: string;
  firstPage: boolean;
  hasQuery: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onSelect: (id: string) => void;
  onReset: () => void;
}) {
  const text = companyMembershipCreationMessages(locale);
  const accountText = managedAccountMessages(locale);
  return (
    <section
      className="app-report-content app-platform-account"
      aria-busy={state.stage === "loading"}
    >
      <AppPageHeader
        title={text.title}
        context={companyName}
        actions={
          <>
            <Link to={backTo}>{text.back}</Link>
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
      <p>{text.selection}</p>
      <AccountSearchBar search={search} locale={locale} hasQuery={hasQuery} onReset={onReset} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.page &&
        (rows.length === 0 ? (
          <p role="status">{accountText.empty}</p>
        ) : (
          <AppResourceTable
            title={accountText.directory}
            rows={rows}
            columns={[
              { id: "name", label: text.name },
              { id: "email", label: text.email },
              { id: "status", label: text.status },
            ]}
            action={{ label: text.choose, onOpen: onSelect, columnId: "name" }}
          />
        ))}
      <fieldset className="app-pagination" aria-label={accountText.pages}>
        <AppButton disabled={firstPage || state.stage === "loading"} onClick={onFirst}>
          {accountText.first}
        </AppButton>
        <AppButton disabled={!state.page?.nextCursor || state.stage === "loading"} onClick={onNext}>
          {accountText.next}
        </AppButton>
      </fieldset>
    </section>
  );
}
