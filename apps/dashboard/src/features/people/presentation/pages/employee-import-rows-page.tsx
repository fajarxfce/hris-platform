import { Text } from "@fluentui/react-components";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { employeeImportMessages } from "../i18n/employee-import-messages";
import type { EmployeeImportRowsState } from "../models/employee-import-rows-state";
import type { employeeImportRowsView, employeeImportRowView } from "../models/employee-import-view";

export function EmployeeImportRowsPage({
  state,
  rows,
  selected,
  locale,
  firstPage,
  onRefresh,
  onFirst,
  onNext,
  onOpen,
  onClose,
}: {
  state: EmployeeImportRowsState;
  rows: ReturnType<typeof employeeImportRowsView>;
  selected: ReturnType<typeof employeeImportRowView> | null;
  locale: Locale;
  firstPage: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
  onClose: () => void;
}) {
  const text = employeeImportMessages(locale);
  return (
    <section className="app-report-content" aria-busy={state.stage === "loading"}>
      <Text className="app-muted">{text.draftNote}</Text>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.page &&
        (rows.length === 0 ? (
          <Text role="status">{text.resultsEmpty}</Text>
        ) : (
          <AppResourceTable
            title={text.rows}
            columns={[
              { id: "number", label: text.number, numeric: true },
              { id: "employeeNumber", label: text.employeeNumber },
              { id: "name", label: text.legalName },
              { id: "status", label: text.status },
              { id: "issues", label: text.issueCount, numeric: true },
            ]}
            rows={rows}
            action={{ label: text.viewRow, onOpen }}
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
        <AppButton onClick={onRefresh} disabled={state.stage === "loading"}>
          {messages(locale).refresh}
        </AppButton>
      </fieldset>
      {selected && (
        <AppDetailsPanel open title={text.rowDetails} closeLabel={text.close} onClose={onClose}>
          <div className="app-report-content">
            <AppPropertyList title={text.rowDetails} items={selected.properties} />
            {selected.issues.length > 0 ? (
              <AppPropertyList title={text.issueDetails} items={selected.issues} />
            ) : (
              <Text>{text.noIssues}</Text>
            )}
            {selected.proposal ? (
              <AppPropertyList title={text.proposal} items={selected.proposal} />
            ) : (
              <Text>{text.noProposal}</Text>
            )}
          </div>
        </AppDetailsPanel>
      )}
    </section>
  );
}
