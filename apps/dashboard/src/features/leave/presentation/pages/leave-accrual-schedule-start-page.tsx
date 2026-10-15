import { Field, useRestoreFocusTarget } from "@fluentui/react-components";
import { ArrowClockwise20Regular, Edit20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { useLeaveAccrualScheduleForm } from "../controllers/use-leave-accrual-schedule-form";
import { leaveAccrualScheduleMessages } from "../i18n/leave-accrual-schedule-messages";
import { leaveBatchMessages } from "../i18n/leave-batch-messages";
import type { LeaveAccrualScheduleStartState } from "../models/leave-accrual-schedule-state";
import type { leaveAccrualScheduleReviewView } from "../models/leave-accrual-schedule-view";
import { LeaveCommandFeedback } from "../widgets/leave-command-feedback";
export function LeaveAccrualScheduleStartPage(props: {
  state: LeaveAccrualScheduleStartState;
  form: ReturnType<typeof useLeaveAccrualScheduleForm>;
  view: ReturnType<typeof leaveAccrualScheduleReviewView> | null;
  locale: Locale;
  companyName: string;
  backTo: string;
  successTo: string | null;
  formId: string;
  onRetry: () => void;
}) {
  const { state, form, view, locale } = props,
    text = leaveAccrualScheduleMessages(locale),
    batch = leaveBatchMessages(locale),
    restore = useRestoreFocusTarget();
  return (
    <section
      className="app-leave-creation"
      aria-busy={state.stage === "preparing" || state.stage === "saving"}
    >
      <AppPageHeader
        title={text.create}
        context={props.companyName}
        actions={<Link to={props.backTo}>{text.back}</Link>}
      />
      <AppCommandBar label={text.create}>
        {["editing", "reviewing", "preparing"].includes(state.stage) && (
          <AppButton
            appearance="primary"
            type="submit"
            form={props.formId}
            disabled={state.stage === "preparing"}
          >
            {state.stage === "reviewing" ? text.schedule : text.review}
          </AppButton>
        )}
        {(state.stage === "reviewing" || state.stage === "conflict") && (
          <AppButton appearance="subtle" icon={<Edit20Regular />} onClick={form.revise}>
            {batch.changeSelection}
          </AppButton>
        )}
        {state.stage === "unavailable" && (
          <AppButton appearance="subtle" icon={<ArrowClockwise20Regular />} onClick={form.reload}>
            {batch.reload}
          </AppButton>
        )}
      </AppCommandBar>
      <div className="app-report-content">
        <AppFailure failure={state.failure} locale={locale} />
        <LeaveCommandFeedback
          stage={state.stage}
          operationId={state.operationId}
          locale={locale}
          successTo={props.successTo}
          successMessage={text.submitted}
          successLabel={text.view}
          onRetry={props.onRetry}
        />
        {(state.stage === "editing" || state.stage === "preparing") && (
          <form
            id={props.formId}
            className="app-editor-form"
            aria-label={text.selection}
            onSubmit={form.prepare}
            noValidate
          >
            <fieldset
              className="app-editor-section app-report-content"
              disabled={state.stage === "preparing"}
            >
              <legend>{text.selection}</legend>
              <Field label={batch.type} required>
                <div className="app-leave-reference">
                  <span>{form.type?.name ?? batch.noneSelected}</span>
                  <AppButton {...restore} appearance="subtle" onClick={form.chooseType}>
                    {batch.chooseType}
                  </AppButton>
                </div>
              </Field>
              <AppTextField
                label={text.firstPeriod}
                type="month"
                min="1900-01"
                max="2199-12"
                required
                {...form.firstPeriod}
              />
              <AppTextField
                label={text.periods}
                type="number"
                min="1"
                max="12"
                step="1"
                required
                {...form.periods}
              />
            </fieldset>
            <p className="app-muted">{text.policyNote}</p>
            {state.stage === "preparing" && <AppLoading label={batch.preparing} />}
          </form>
        )}
        {view && (
          <>
            <AppPropertyList title={text.review} items={view.properties} />
            <AppResourceTable
              title={text.periods}
              columns={[
                { id: "period", label: batch.period },
                { id: "due", label: text.due },
                { id: "days", label: batch.days, numeric: true },
                { id: "policy", label: batch.policy },
                { id: "service", label: batch.serviceMonths, numeric: true },
                { id: "contracts", label: batch.contracts },
              ]}
              rows={view.rows}
            />
            <p className="app-muted">{text.reviewNote}</p>
            {state.stage === "reviewing" && (
              <form
                id={props.formId}
                className="app-editor-form"
                aria-label={text.create}
                onSubmit={form.submit}
                noValidate
              >
                <AppTextArea
                  label={batch.reason}
                  rows={3}
                  maxLength={1000}
                  required
                  {...form.reason}
                />
              </form>
            )}
          </>
        )}
      </div>
    </section>
  );
}
