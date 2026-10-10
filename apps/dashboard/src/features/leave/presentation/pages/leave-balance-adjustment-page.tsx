import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useLeaveBalanceAdjustmentForm } from "../controllers/use-leave-balance-adjustment-form";
import { leaveAdjustmentMessages } from "../i18n/leave-adjustment-messages";
import { leaveBalanceMessages } from "../i18n/leave-balance-messages";
import type { LeaveBalanceAdjustmentState } from "../models/leave-balance-adjustment-state";
import type { leaveBalanceEmployeeView, leaveLedgerView } from "../models/leave-balance-view";

export function LeaveBalanceAdjustmentPage({
  state,
  form,
  employee,
  view,
  companyName,
  locale,
  backTo,
  fromTypes,
  ledgerTo,
  onRetry,
}: {
  state: LeaveBalanceAdjustmentState;
  form: ReturnType<typeof useLeaveBalanceAdjustmentForm>;
  employee: ReturnType<typeof leaveBalanceEmployeeView> | null;
  view: ReturnType<typeof leaveLedgerView> | null;
  companyName: string;
  locale: Locale;
  backTo: string;
  fromTypes: boolean;
  ledgerTo: string;
  onRetry: () => void;
}) {
  const text = leaveAdjustmentMessages(locale);
  const balance = leaveBalanceMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <section
      className="app-report-content app-leave-details"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={text.title}
        context={companyName}
        actions={
          <Link {...restore} to={backTo}>
            {fromTypes ? text.backToTypes : text.backToLedger}
          </Link>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.stage === "saving" && <AppLoading label={text.saving} />}
      {state.stage === "unavailable" && (
        <AppButton {...restore} onClick={form.refresh}>
          {text.reload}
        </AppButton>
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
            {text.saved}
            <div className="app-reference">
              {balance.entryId}: {state.receipt?.id}
            </div>
            <Link {...restore} to={ledgerTo}>
              {text.view}
            </Link>
          </MessageBarBody>
        </MessageBar>
      )}
      {employee && <AppPropertyList title={balance.employee} items={employee} />}
      {view && (
        <>
          <AppPropertyList title={balance.summary} items={view.properties} />
          <form className="app-editor-form" onSubmit={form.submit} noValidate>
            <AppTextField
              label={text.days}
              {...form.days}
              inputMode="decimal"
              maxLength={8}
              required
              hint={text.daysHint}
              error={state.failure?.fields.days ? text.daysError : undefined}
              readOnly={!form.editable}
            />
            <AppTextArea
              label={text.reason}
              {...form.reason}
              maxLength={1000}
              required
              error={state.failure?.fields.reason ? text.reasonError : undefined}
              readOnly={!form.editable}
            />
            <div className="app-form-actions">
              <AppButton appearance="primary" type="submit" disabled={!form.editable}>
                {text.confirm}
              </AppButton>
              {(state.stage === "editing" || state.stage === "conflict") && (
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
