import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { lifecycleCaseMessages } from "../i18n/lifecycle-case-messages";
import type { AssignedLifecycleTasksState } from "../models/assigned-lifecycle-tasks-state";
import type { assignedLifecycleTasksView } from "../models/lifecycle-case-view";

export function AssignedLifecycleTasksPage({
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
  state: AssignedLifecycleTasksState;
  rows: ReturnType<typeof assignedLifecycleTasksView>;
  companyName: string;
  locale: Locale;
  firstPage: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const text = lifecycleCaseMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.queue}
        context={companyName}
        actions={
          <AppButton
            icon={<ArrowClockwise20Regular />}
            disabled={state.stage === "loading"}
            onClick={onRefresh}
          >
            {messages(locale).refresh}
          </AppButton>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      <div className="app-report-content">
        {state.page &&
          (rows.length === 0 ? (
            <Text role="status">{text.queueEmpty}</Text>
          ) : (
            <AppResourceTable
              title={text.queue}
              columns={[
                { id: "task", label: text.task },
                { id: "employee", label: text.employee },
                { id: "number", label: text.employeeNumber },
                { id: "kind", label: text.kind },
                { id: "due", label: text.dueDate },
              ]}
              rows={rows}
              action={{ label: text.viewTask, onOpen }}
            />
          ))}
        <fieldset className="app-pagination" aria-label={text.taskPages}>
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
