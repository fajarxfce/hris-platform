import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useLeaveActionForm } from "../controllers/use-leave-action-form";
import { leaveActionMessages } from "../i18n/leave-action-messages";
import { leaveMessages } from "../i18n/leave-messages";
import type { LeaveActionState } from "../models/leave-action-state";
import type { leaveActionCopy, leaveActionView } from "../models/leave-action-view";

export function LeaveActionPage({
  state,
  form,
  view,
  copy,
  companyName,
  locale,
  timezone,
  backTo,
  onRetry,
}: {
  state: LeaveActionState;
  form: ReturnType<typeof useLeaveActionForm>;
  view: ReturnType<typeof leaveActionView> | null;
  copy: ReturnType<typeof leaveActionCopy>;
  companyName: string;
  locale: Locale;
  timezone: string;
  backTo: string;
  onRetry: () => void;
}) {
  const text = leaveActionMessages(locale);
  const common = leaveMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <section
      className="app-report-content app-leave-action"
      aria-busy={state.stage === "loading" || state.stage === "submitting"}
    >
      <AppPageHeader
        title={copy.title}
        context={companyName}
        actions={
          <Link {...restore} to={backTo}>
            {text.back}
          </Link>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.stage === "unavailable" && (
        <AppButton onClick={form.refresh}>{messages(locale).retry}</AppButton>
      )}
      {state.stage === "unconfirmed" && (
        <MessageBar intent="warning" layout="multiline" role="status">
          <MessageBarBody>
            {text.unconfirmed}
            <div className="app-reference">
              {text.operation}: {state.operationId}
            </div>
            <AppButton onClick={onRetry}>{text.retry}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "saved" && (
        <MessageBar intent="success" layout="multiline" role="status">
          <MessageBarBody>
            {text.saved} {text.version}: {state.receipt?.version}.{" "}
            <Link {...restore} to={backTo}>
              {text.view}
            </Link>
          </MessageBarBody>
        </MessageBar>
      )}
      {view && (
        <>
          <AppPropertyList title={text.review} items={view.properties} />
          <AppResourceTable
            title={`${common.schedule} (${timezone})`}
            columns={[
              { id: "date", label: common.date },
              { id: "portion", label: common.portion },
              { id: "start", label: common.start },
              { id: "end", label: common.end },
              { id: "planned", label: common.planned, numeric: true },
              { id: "charged", label: common.charged, numeric: true },
            ]}
            rows={view.days}
          />
          <AppPropertyList title={text.workflow} items={view.workflow} />
          <form className="app-editor-form" onSubmit={form.submit} noValidate>
            <p>{copy.hint}</p>
            <AppTextField
              label={text.reason}
              {...form.reason}
              maxLength={1000}
              required={form.required}
              readOnly={!form.editable}
            />
            {state.stage === "submitting" && <AppLoading label={text.saving} />}
            <div className="app-form-actions">
              <AppButton appearance="primary" type="submit" disabled={!form.editable}>
                {text.confirm}
              </AppButton>
              {(state.stage === "reviewing" || state.stage === "conflict") && (
                <AppButton {...restore} onClick={form.refresh}>
                  {text.reload}
                </AppButton>
              )}
            </div>
          </form>
        </>
      )}
    </section>
  );
}
