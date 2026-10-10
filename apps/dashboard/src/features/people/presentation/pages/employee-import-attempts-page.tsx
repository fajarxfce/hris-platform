import { Text } from "@fluentui/react-components";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { employeeImportMessages } from "../i18n/employee-import-messages";
import type { EmployeeImportAttemptsState } from "../models/employee-import-attempts-state";
import type { employeeImportAttemptsView } from "../models/employee-import-view";

export function EmployeeImportAttemptsPage({
  state,
  rows,
  locale,
  firstPage,
  onRefresh,
  onFirst,
  onNext,
}: {
  state: EmployeeImportAttemptsState;
  rows: ReturnType<typeof employeeImportAttemptsView>;
  locale: Locale;
  firstPage: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
}) {
  const text = employeeImportMessages(locale);
  return (
    <section className="app-report-content" aria-busy={state.stage === "loading"}>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.page &&
        (rows.length === 0 ? (
          <Text role="status">{text.resultsEmpty}</Text>
        ) : (
          <AppResourceTable
            title={text.attempts}
            columns={[
              { id: "phase", label: text.phase },
              { id: "job", label: text.jobId },
              { id: "actor", label: text.actorId },
              { id: "date", label: text.createdAt },
            ]}
            rows={rows}
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
    </section>
  );
}
