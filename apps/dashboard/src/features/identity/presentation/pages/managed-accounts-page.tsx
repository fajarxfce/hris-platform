import { ArrowClockwise20Regular, Search20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useManagedAccountSearch } from "../controllers/use-managed-account-search";
import { managedAccountMessages } from "../i18n/managed-account-messages";
import type { managedAccountRows } from "../models/managed-account-view";
import type { ManagedAccountsState } from "../models/managed-accounts-state";

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
          <AppButton
            appearance="subtle"
            icon={<ArrowClockwise20Regular />}
            aria-label={messages(locale).refresh}
            disabled={state.stage === "loading"}
            onClick={onRefresh}
          />
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      <search aria-label={text.search}>
        <form className="app-command-bar app-command-bar-panel" onSubmit={search.submit} noValidate>
          <AppTextField
            label={text.search}
            placeholder={text.searchHint}
            type="search"
            contentBefore={<Search20Regular />}
            {...search.query}
            maxLength={100}
            error={search.invalid ? text.invalidSearch : undefined}
          />
          <AppButton type="submit" appearance="subtle" icon={<Search20Regular />}>
            {text.searchAction}
          </AppButton>
          {hasQuery && (
            <AppButton appearance="subtle" onClick={onReset}>
              {text.reset}
            </AppButton>
          )}
        </form>
      </search>
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
