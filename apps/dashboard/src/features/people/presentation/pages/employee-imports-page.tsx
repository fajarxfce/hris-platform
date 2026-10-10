import { Text } from "@fluentui/react-components";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { employeeImportMessages } from "../i18n/employee-import-messages";
import type { employeeImportsView } from "../models/employee-import-view";
import type { EmployeeImportsState } from "../models/employee-imports-state";

export function EmployeeImportsPage({
  state,
  rows,
  companyName,
  locale,
  firstPage,
  onRefresh,
  onFirst,
  onNext,
  onOpen,
}: {
  state: EmployeeImportsState;
  rows: ReturnType<typeof employeeImportsView>;
  companyName: string;
  locale: Locale;
  firstPage: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const text = employeeImportMessages(locale);
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
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.page &&
          (rows.length === 0 ? (
            <Text role="status">{text.empty}</Text>
          ) : (
            <AppResourceTable
              title={text.title}
              columns={[
                { id: "file", label: text.fileName },
                { id: "status", label: text.status },
                { id: "rows", label: text.rowCount, numeric: true },
                { id: "date", label: text.createdAt },
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
