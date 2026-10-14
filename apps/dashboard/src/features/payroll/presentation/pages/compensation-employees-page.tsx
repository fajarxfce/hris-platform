import { ArrowSync20Regular, Search20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { usePayrollEmployeeSearch } from "../controllers/use-payroll-employee-search";
import { compensationMessages } from "../i18n/compensation-messages";
import type { CompensationEmployeesState } from "../models/compensation-employees-state";
export function CompensationEmployeesPage({
  state,
  filter,
  locale,
  companyName,
  firstPage,
  onOpen,
  onRefresh,
  onFirst,
  onNext,
}: {
  state: CompensationEmployeesState;
  filter: ReturnType<typeof usePayrollEmployeeSearch>;
  locale: Locale;
  companyName: string;
  firstPage: boolean;
  onOpen: (id: string) => void;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
}) {
  const text = compensationMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader title={text.title} context={companyName} />
      <AppCommandBar label={text.title}>
        <AppButton
          appearance="subtle"
          icon={<ArrowSync20Regular />}
          onClick={onRefresh}
          disabled={state.stage === "loading"}
        >
          {messages(locale).refresh}
        </AppButton>
      </AppCommandBar>
      <AppFailure failure={state.failure} locale={locale} />
      <div className="app-report-content">
        <form className="app-resource-filters" onSubmit={filter.submit}>
          <AppTextField
            label={text.search}
            {...filter.field}
            contentBefore={<Search20Regular />}
            maxLength={120}
          />
          <AppButton type="submit">{text.searchAction}</AppButton>
        </form>
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.stage === "ready" && state.page && (
          <>
            {state.page.items.length ? (
              <AppResourceTable
                title={text.employees}
                minWidth={460}
                columns={[
                  { id: "number", label: text.employeeNumber },
                  { id: "name", label: text.name },
                ]}
                rows={state.page.items.map((employee) => ({
                  id: employee.id,
                  actionLabel: employee.name,
                  cells: [employee.employeeNumber, employee.name],
                }))}
                action={{ label: text.open, onOpen }}
              />
            ) : (
              <p role="status">{text.empty}</p>
            )}
            <fieldset className="app-pagination" aria-label={text.pages}>
              <AppButton onClick={onFirst} disabled={firstPage}>
                {text.first}
              </AppButton>
              <AppButton onClick={onNext} disabled={state.page.nextCursor === null}>
                {text.next}
              </AppButton>
            </fieldset>
          </>
        )}
      </div>
    </section>
  );
}
