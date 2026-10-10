import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { LifecycleTaskStatus } from "../../domain/entities/lifecycle-task";
import type { useLifecycleTaskForm } from "../controllers/use-lifecycle-task-form";
import { lifecycleCaseMessages } from "../i18n/lifecycle-case-messages";
import type { LifecycleTaskEditorState } from "../models/lifecycle-task-editor-state";

export function LifecycleTaskPanel({
  title,
  properties,
  locale,
  statuses,
  assignable,
  state,
  form,
  onRetry,
}: {
  title: string;
  properties: readonly Readonly<{ label: string; value: string }>[];
  locale: Locale;
  statuses: readonly LifecycleTaskStatus[];
  assignable: boolean;
  state: LifecycleTaskEditorState;
  form: ReturnType<typeof useLifecycleTaskForm>;
  onRetry: () => void;
}) {
  const text = lifecycleCaseMessages(locale);
  const restoreFocus = useRestoreFocusTarget();
  return (
    <AppDetailsPanel open={true} title={title} closeLabel={text.close} onClose={form.close}>
      <div className="app-report-content" aria-busy={state.stage === "saving"}>
        <AppPropertyList title={text.task} items={properties} />
        {assignable && (
          <AppButton {...restoreFocus} onClick={form.assign} disabled={!form.editable}>
            {text.assignTask}
          </AppButton>
        )}
        <AppFailure failure={state.failure} locale={locale} />
        {state.stage === "unconfirmed" && (
          <MessageBar intent="warning" role="status">
            <MessageBarBody>
              {text.unconfirmed}
              <div className="app-reference">
                {text.operation}: {state.operationId}
              </div>
              <AppButton onClick={onRetry}>{text.retrySave}</AppButton>
            </MessageBarBody>
          </MessageBar>
        )}
        {statuses.length > 0 && (
          <form className="app-editor-form" onSubmit={form.submit} noValidate>
            <AppSelect label={text.newStatus} {...form.status} disabled={!form.editable}>
              {statuses.map((status) => (
                <option key={status} value={status}>
                  {text[status]}
                </option>
              ))}
            </AppSelect>
            <AppTextArea
              label={text.reason}
              {...form.reason}
              required
              maxLength={1000}
              readOnly={!form.editable}
              resize="vertical"
            />
            {state.stage === "saving" && <AppLoading label={text.saving} />}
            <div className="app-form-actions">
              <AppButton type="submit" appearance="primary" disabled={!form.editable}>
                {text.saveTask}
              </AppButton>
              {(state.stage === "editing" || state.stage === "conflict") && (
                <AppButton {...restoreFocus} onClick={form.reload}>
                  {text.reloadTask}
                </AppButton>
              )}
            </div>
          </form>
        )}
      </div>
    </AppDetailsPanel>
  );
}
