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
import type { LifecycleCaseSearch } from "../../domain/entities/lifecycle-case-search";
import { lifecycleCaseMessages } from "../i18n/lifecycle-case-messages";
import type { lifecycleCasesView } from "../models/lifecycle-case-view";
import type { LifecycleCasesState } from "../models/lifecycle-cases-state";

export function LifecycleCasesPage({
  state,
  rows,
  search,
  companyName,
  locale,
  templatesTo,
  onStatus,
  onClearEmployee,
  onRefresh,
  onFirst,
  onNext,
  onOpen,
}: {
  state: LifecycleCasesState;
  rows: ReturnType<typeof lifecycleCasesView>;
  search: LifecycleCaseSearch;
  companyName: string;
  locale: Locale;
  templatesTo: string;
  onStatus: (status: string) => void;
  onClearEmployee: () => void;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const text = lifecycleCaseMessages(locale);
  const shared = messages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={companyName}
        actions={
          <>
            <Link to={templatesTo}>{text.templates}</Link>
            <AppButton
              icon={<ArrowClockwise20Regular />}
              disabled={state.stage === "loading"}
              onClick={onRefresh}
            >
              {shared.refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      <div className="app-report-content">
        <div className="app-filter-actions">
          <AppSelect
            label={text.status}
            value={search.status}
            onChange={(_event, data) => onStatus(data.value)}
          >
            <option value="">{text.all}</option>
            <option value="OPEN">{text.OPEN}</option>
            <option value="COMPLETED">{text.COMPLETED}</option>
            <option value="CANCELLED">{text.CANCELLED}</option>
          </AppSelect>
          {search.employmentId !== null && (
            <AppButton onClick={onClearEmployee}>{text.clearEmployee}</AppButton>
          )}
        </div>
        {state.stage === "loading" && <AppLoading label={shared.loading} />}
        {state.page &&
          (rows.length === 0 ? (
            <Text role="status">{text.empty}</Text>
          ) : (
            <AppResourceTable
              title={text.cases}
              columns={[
                { id: "employee", label: text.employee },
                { id: "number", label: text.employeeNumber },
                { id: "kind", label: text.kind },
                { id: "status", label: text.status },
                { id: "date", label: text.targetDate },
                { id: "tasks", label: text.progress, numeric: true },
              ]}
              rows={rows}
              action={{ label: text.view, onOpen }}
            />
          ))}
        <fieldset className="app-pagination" aria-label={text.pages}>
          <AppButton
            onClick={onFirst}
            disabled={search.after === null || state.stage === "loading"}
          >
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
