import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { useLifecycleTaskAssignmentForm } from "../controllers/use-lifecycle-task-assignment-form";
import { lifecycleCaseMessages } from "../i18n/lifecycle-case-messages";
import type { LifecycleTaskEditorState } from "../models/lifecycle-task-editor-state";

export function LifecycleTaskAssignmentPage({
  title,
  properties,
  form,
  state,
  locale,
  canRemove,
  onRetry,
}: {
  title: string;
  properties: readonly Readonly<{ label: string; value: string }>[];
  form: ReturnType<typeof useLifecycleTaskAssignmentForm>;
  state: LifecycleTaskEditorState;
  locale: Locale;
  canRemove: boolean;
  onRetry: () => void;
}) {
  const text = lifecycleCaseMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <AppDetailsPanel
      open
      title={`${text.assignTask}: ${title}`}
      closeLabel={text.close}
      onClose={form.close}
    >
      <div className="app-report-content" aria-busy={state.stage === "saving"}>
        <AppButton {...restore} onClick={form.back} disabled={state.stage === "saving"}>
          {text.backToTask}
        </AppButton>
        <AppPropertyList title={text.task} items={properties} />
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
        <form className="app-editor-form" onSubmit={form.submit} noValidate>
          <section className="app-assignment" aria-label={text.newAssignee}>
            <strong>{text.newAssignee}</strong>
            <span>
              {form.unassign ? text.unassigned : (form.member?.displayName ?? text.noMember)}
            </span>
            {form.member && <small className="app-reference">{form.member.id}</small>}
            <div className="app-form-actions">
              <AppButton {...restore} onClick={form.choose} disabled={!form.editable}>
                {text.chooseMember}
              </AppButton>
              {canRemove && (
                <AppButton onClick={form.remove} disabled={!form.editable}>
                  {text.removeAssignee}
                </AppButton>
              )}
            </div>
          </section>
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
            <AppButton appearance="primary" type="submit" disabled={!form.editable}>
              {text.saveAssignment}
            </AppButton>
            {(state.stage === "editing" || state.stage === "conflict") && (
              <AppButton {...restore} onClick={form.reload}>
                {text.reloadTask}
              </AppButton>
            )}
          </div>
        </form>
      </div>
    </AppDetailsPanel>
  );
}
