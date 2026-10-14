import { ArrowSync20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { taxOpeningEmployeeStatuses } from "../../domain/entities/tax-opening-review";
import type { useTaxOpeningFilter } from "../controllers/use-tax-opening-filter";
import { taxOpeningMessages } from "../i18n/tax-opening-messages";
import type { TaxOpeningEmployeesState } from "../models/tax-opening-employees-state";
export function TaxOpeningEmployeesPage({
  state,
  filter,
  locale,
  companyName,
  first,
  onRefresh,
  onFirst,
  onNext,
  onOpen,
}: {
  state: TaxOpeningEmployeesState;
  filter: ReturnType<typeof useTaxOpeningFilter>;
  locale: Locale;
  companyName: string;
  first: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const text = taxOpeningMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader title={text.title} context={companyName} />
      <AppCommandBar label={text.title}>
        <AppButton
          appearance="subtle"
          icon={<ArrowSync20Regular />}
          disabled={state.stage === "loading"}
          onClick={onRefresh}
        >
          {messages(locale).refresh}
        </AppButton>
      </AppCommandBar>
      <AppFailure failure={state.failure} locale={locale} />
      <div className="app-report-content">
        <form className="app-resource-filters" onSubmit={filter.submit}>
          <AppTextField
            label={text.year}
            {...filter.year}
            type="number"
            min="2024"
            max="2100"
            required
          />
          <AppTextField label={text.search} {...filter.query} type="search" maxLength={120} />
          <AppSelect label={text.status} {...filter.status}>
            <option value="">{text.all}</option>
            {taxOpeningEmployeeStatuses.map((value) => (
              <option key={value} value={value}>
                {text[value]}
              </option>
            ))}
          </AppSelect>
          <AppButton type="submit">{text.apply}</AppButton>
        </form>
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.stage === "ready" && state.page && (
          <>
            <AppResourceTable
              title={text.title}
              minWidth={700}
              columns={[
                { id: "number", label: text.employeeNumber },
                { id: "name", label: text.name },
                { id: "status", label: text.status },
                { id: "version", label: text.version, numeric: true },
              ]}
              rows={state.page.items.map((row) => ({
                id: row.id,
                actionLabel: row.name,
                cells: [
                  row.employeeNumber,
                  row.name,
                  text[row.openingStatus ?? "UNPREPARED"],
                  row.openingVersion === null ? "—" : String(row.openingVersion),
                ],
              }))}
              action={{ label: text.open, onOpen }}
            />
            {!state.page.items.length && <p role="status">{text.empty}</p>}
            <fieldset className="app-pagination" aria-label={text.pages}>
              <AppButton onClick={onFirst} disabled={first}>
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
