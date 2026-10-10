import { Text } from "@fluentui/react-components";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useLeavePolicyFilters } from "../controllers/use-leave-policy-filters";
import { leavePolicyMessages } from "../i18n/leave-policy-messages";
import type { LeavePoliciesState } from "../models/leave-policies-state";
import type { leavePoliciesView } from "../models/leave-policy-view";

export function LeavePoliciesPage({
  state,
  rows,
  companyName,
  locale,
  filters,
  firstPage,
  onRefresh,
  onFirst,
  onNext,
  onOpen,
}: {
  state: LeavePoliciesState;
  rows: ReturnType<typeof leavePoliciesView>;
  companyName: string;
  locale: Locale;
  filters: ReturnType<typeof useLeavePolicyFilters>;
  firstPage: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const text = leavePolicyMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={companyName}
        actions={
          <AppButton disabled={state.stage === "loading"} onClick={onRefresh}>
            {messages(locale).refresh}
          </AppButton>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      <div className="app-report-content">
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
              columns={[
                { id: "code", label: text.code },
                { id: "name", label: text.name },
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
