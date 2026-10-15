import { Field, useRestoreFocusTarget } from "@fluentui/react-components";
import { ArrowClockwise20Regular, Edit20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { LeaveBatchKind } from "../../domain/entities/leave-batch";
import type { useLeaveBatchForm } from "../controllers/use-leave-batch-form";
import { leaveBatchMessages } from "../i18n/leave-batch-messages";
import type { LeaveBatchStartState } from "../models/leave-batch-state";
import type { leaveBatchReviewView } from "../models/leave-batch-view";
import { LeaveCommandFeedback } from "../widgets/leave-command-feedback";
export function LeaveBatchStartPage(props: {
  state: LeaveBatchStartState;
  form: ReturnType<typeof useLeaveBatchForm>;
  view: ReturnType<typeof leaveBatchReviewView> | null;
  kind: LeaveBatchKind;
  locale: Locale;
  companyName: string;
  backTo: string;
  successTo: string | null;
  formId: string;
  onRetry: () => void;
}) {
  const { state, form, locale, view } = props,
    text = leaveBatchMessages(locale),
    restore = useRestoreFocusTarget(),
    title = props.kind === "ACCRUAL" ? text.newAccrual : text.newYearClose;
  return (
    <section
      className="app-leave-creation"
      aria-busy={state.stage === "preparing" || state.stage === "saving"}
    >
      <AppPageHeader
        title={title}
        context={props.companyName}
        actions={<Link to={props.backTo}>{text.back}</Link>}
      />
      <AppCommandBar label={title}>
        {(state.stage === "editing" ||
          state.stage === "reviewing" ||
          state.stage === "preparing") && (
          <AppButton
            appearance="primary"
            form={props.formId}
            type="submit"
            disabled={state.stage === "preparing"}
          >
            {state.stage === "reviewing" ? title : text.review}
          </AppButton>
        )}
        {(state.stage === "reviewing" || state.stage === "conflict") && (
          <AppButton appearance="subtle" icon={<Edit20Regular />} onClick={form.revise}>
            {text.changeSelection}
          </AppButton>
        )}
        {state.stage === "unavailable" && (
          <AppButton appearance="subtle" icon={<ArrowClockwise20Regular />} onClick={form.reload}>
            {text.reload}
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
          onRetry={props.onRetry}
        />
        {(state.stage === "editing" || state.stage === "preparing") && (
          <form
            id={props.formId}
            className="app-editor-form"
            aria-label={title}
            onSubmit={form.prepare}
            noValidate
          >
            <fieldset
              className="app-editor-section app-report-content"
              disabled={state.stage === "preparing"}
            >
              <legend>{text.selection}</legend>
              <Field label={text.type} required>
                <div className="app-leave-reference">
                  <span>{form.type?.name ?? text.noneSelected}</span>
                  <AppButton {...restore} appearance="subtle" onClick={form.chooseType}>
                    {text.chooseType}
                  </AppButton>
                </div>
              </Field>
              <AppTextField
                label={props.kind === "ACCRUAL" ? text.month : text.year}
                type={props.kind === "ACCRUAL" ? "month" : "number"}
                min={props.kind === "ACCRUAL" ? "1900-01" : "1900"}
                max={props.kind === "ACCRUAL" ? "2199-12" : "2199"}
                required
                {...form.period}
              />
            </fieldset>
            <p className="app-muted">
              {props.kind === "ACCRUAL" ? text.accrualNote : text.closeNote}
            </p>
            {state.stage === "preparing" && <AppLoading label={text.preparing} />}
          </form>
        )}
        {view && (
          <div className="app-leave-creation-layout">
            <div className="app-report-content">
              <AppPropertyList title={text.review} items={view.properties} />
              <p className="app-muted">{text.reviewNote}</p>
              {state.stage === "reviewing" && (
                <form
                  id={props.formId}
                  className="app-editor-form"
                  aria-label={title}
                  onSubmit={form.submit}
                  noValidate
                >
                  <AppTextArea
                    label={text.reason}
                    rows={3}
                    maxLength={1000}
                    required
                    {...form.reason}
                  />
                  <p className="app-muted">
                    {props.kind === "ACCRUAL" ? text.accrualNote : text.closeNote}
                  </p>
                </form>
              )}
            </div>
            <aside className="app-leave-creation-review">
              <AppPropertyList title={text.policy} items={view.policy} />
            </aside>
          </div>
        )}
      </div>
    </section>
  );
}
