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
import type { useOvertimeActionForm } from "../controllers/use-overtime-action-form";
import { overtimeActionMessages } from "../i18n/overtime-action-messages";
import { overtimeMessages } from "../i18n/overtime-messages";
import type { OvertimeActionState } from "../models/overtime-action-state";
import type { overtimeActionCopy } from "../models/overtime-action-view";
import type { overtimeDetailsView } from "../models/overtime-view";

export function OvertimeActionPage(props: {
  state: OvertimeActionState;
  form: ReturnType<typeof useOvertimeActionForm>;
  view: ReturnType<typeof overtimeDetailsView> | null;
  copy: ReturnType<typeof overtimeActionCopy>;
  actual: boolean;
  companyName: string;
  locale: Locale;
  backTo: string;
  onRetry: () => void;
}) {
  const { state, form, view, copy, locale } = props;
  const text = overtimeActionMessages(locale),
    common = overtimeMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <section
      className="app-report-content app-record-editor app-overtime-action"
      aria-busy={state.stage === "loading" || state.stage === "submitting"}
    >
      <AppPageHeader
        title={copy.title}
        context={props.companyName}
        actions={
          <Link {...restore} to={props.backTo}>
            {text.back}
          </Link>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.stage === "submitting" && <AppLoading label={text.saving} />}
      {state.stage === "unavailable" && (
        <div>
          <AppButton onClick={form.refresh}>{messages(locale).retry}</AppButton>
        </div>
      )}
      {state.stage === "unconfirmed" && (
        <MessageBar intent="warning" layout="multiline" role="status">
          <MessageBarBody>
            {text.unconfirmed}
            <div className="app-reference">
              {text.operation}: {state.operationId}
            </div>
            <AppButton onClick={props.onRetry}>{text.retry}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "saved" && (
        <MessageBar intent="success" layout="multiline" role="status">
          <MessageBarBody>
            {copy.saved} {text.version}: {state.receipt?.version}.{" "}
            <Link {...restore} to={props.backTo}>
              {text.view}
            </Link>
          </MessageBarBody>
        </MessageBar>
      )}
      {view && (
        <>
          <AppPropertyList
            title={text.review}
            items={[
              ...view.properties,
              { label: text.version, value: state.review?.request.version },
            ]}
          />
          <AppResourceTable
            title={common.intervals}
            columns={[
              { id: "record", label: common.period },
              { id: "start", label: common.start },
              { id: "end", label: common.end },
              { id: "break", label: common.break, numeric: true },
              { id: "duration", label: common.duration, numeric: true },
            ]}
            rows={view.intervals}
          />
          <AppPropertyList title={common.schedule} items={view.schedule} />
          {view.approval && <AppPropertyList title={common.approval} items={view.approval} />}
          <form
            className="app-editor-form"
            aria-label={copy.title}
            onSubmit={form.submit}
            noValidate
          >
            <p>{copy.hint}</p>
            {props.actual && (
              <fieldset className="app-editor-section app-report-content">
                <legend>
                  {text.actualTime} · {state.review?.request.timezone}
                </legend>
                <div className="app-editor-fields">
                  <AppTextField
                    label={text.startsAt}
                    {...form.startsAt}
                    type="datetime-local"
                    step={60}
                    required
                    readOnly={!form.editable}
                  />
                  <AppTextField
                    label={text.endsAt}
                    {...form.endsAt}
                    type="datetime-local"
                    step={60}
                    required
                    readOnly={!form.editable}
                  />
                  <AppTextField
                    label={text.breakMinutes}
                    {...form.breakMinutes}
                    type="number"
                    min={0}
                    max={120}
                    step={1}
                    required
                    readOnly={!form.editable}
                  />
                </div>
                <p className="app-muted">{text.bounds}</p>
                {form.invalidLocalTime && (
                  <MessageBar intent="error" role="alert" layout="multiline">
                    <MessageBarBody>{text.invalidLocalTime}</MessageBarBody>
                  </MessageBar>
                )}
              </fieldset>
            )}
            <AppTextField
              label={text.reason}
              {...form.reason}
              required={form.required}
              maxLength={1000}
              readOnly={!form.editable}
            />
            <div className="app-form-actions">
              <AppButton appearance="primary" type="submit" disabled={!form.editable}>
                {copy.title}
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
