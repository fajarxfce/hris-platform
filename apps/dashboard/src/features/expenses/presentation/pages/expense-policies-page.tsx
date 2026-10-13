import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useExpensePolicyFilters } from "../controllers/use-expense-policy-filters";
import { expensePolicyEditorMessages } from "../i18n/expense-policy-editor-messages";
import { expensePolicyMessages } from "../i18n/expense-policy-messages";
import type { ExpensePoliciesState } from "../models/expense-policies-state";
import type { expensePoliciesView } from "../models/expense-policy-view";

export function ExpensePoliciesPage({
  state,
  rows,
  companyName,
  locale,
  filters,
  createTo,
  firstPage,
  onRefresh,
  onFirst,
  onNext,
  onOpen,
}: {
  state: ExpensePoliciesState;
  rows: ReturnType<typeof expensePoliciesView>;
  companyName: string;
  locale: Locale;
  filters: ReturnType<typeof useExpensePolicyFilters>;
  createTo: string | null;
  firstPage: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const text = expensePolicyMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={companyName}
        actions={
          <>
            {createTo && <Link to={createTo}>{expensePolicyEditorMessages(locale).create}</Link>}
            <AppButton
              appearance="transparent"
              icon={<ArrowClockwise20Regular />}
              disabled={state.stage === "loading"}
              onClick={onRefresh}
            >
              {messages(locale).refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      <div className="app-report-content">
        <p className="app-muted">{text.selection}</p>
        <form className="app-resource-filters" aria-label={text.filters} onSubmit={filters.submit}>
          <AppSelect label={text.active} {...filters.active}>
            <option value="">{text.all}</option>
            <option value="true">{text.enabled}</option>
            <option value="false">{text.disabled}</option>
          </AppSelect>
          <AppButton type="submit">{text.apply}</AppButton>
        </form>
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.page &&
          (rows.length === 0 ? (
            <Text role="status">{text.empty}</Text>
          ) : (
            <AppResourceTable
              title={text.title}
              minWidth={880}
              columns={[
                { id: "code", label: text.code },
                { id: "name", label: text.name },
                { id: "amount", label: text.maximumLine, numeric: true },
                { id: "from", label: text.from },
                { id: "active", label: text.active },
                { id: "version", label: text.version, numeric: true },
              ]}
              rows={rows}
              action={{ label: text.view, onOpen }}
            />
          ))}
        <fieldset className="app-pagination" aria-label={text.pages}>
          <AppButton onClick={onFirst} disabled={firstPage || state.stage === "loading"}>
            {text.first}
          </AppButton>
          <AppButton
            onClick={onNext}
            disabled={state.page?.nextCursor == null || state.stage === "loading"}
          >
            {text.next}
          </AppButton>
        </fieldset>
      </div>
    </section>
  );
}
