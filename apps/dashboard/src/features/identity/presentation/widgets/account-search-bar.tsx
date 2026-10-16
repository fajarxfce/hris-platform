import { Search20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { useManagedAccountSearch } from "../controllers/use-managed-account-search";
import { managedAccountMessages } from "../i18n/managed-account-messages";

export function AccountSearchBar({
  search,
  locale,
  hasQuery,
  onReset,
}: {
  search: ReturnType<typeof useManagedAccountSearch>;
  locale: Locale;
  hasQuery: boolean;
  onReset: () => void;
}) {
  const text = managedAccountMessages(locale);
  return (
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
  );
}
