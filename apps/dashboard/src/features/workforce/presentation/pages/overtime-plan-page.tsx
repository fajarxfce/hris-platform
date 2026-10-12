import {
  Field,
  MessageBar,
  MessageBarBody,
  useRestoreFocusTarget,
} from "@fluentui/react-components";
import { PersonSearch20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useOvertimePlanForm } from "../controllers/use-overtime-plan-form";
import { overtimePlanningMessages } from "../i18n/overtime-planning-messages";
import type { OvertimePlanState } from "../models/overtime-plan-state";
import type { overtimePlanningView } from "../models/overtime-planning-view";

export function OvertimePlanPage(props: {
  state: OvertimePlanState;
  form: ReturnType<typeof useOvertimePlanForm>;
  view: ReturnType<typeof overtimePlanningView> | null;
  companyName: string;
  locale: Locale;
  backTo: string;
  savedTo: string | null;
  onRetry: () => void;
}) {
  const { state, form, view, locale } = props,
    text = overtimePlanningMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <section
      className="app-report-content app-record-editor app-overtime-plan"
      aria-busy={state.stage === "loading" || state.stage === "submitting"}
    >
      <AppPageHeader
        title={text.title}
        context={props.companyName}
        actions={
          <Link {...restore} to={props.backTo}>
            {text.back}
          </Link>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage !== "saved" && (
        <div className="app-editor-fields app-overtime-selection">
          <AppTextField
            label={text.workDate}
            type="date"
            min="2000-01-01"
            max="2100-12-31"
            value={state.workDate}
            onChange={form.changeDate}
            readOnly={!form.selectionEnabled}
            required
          />
          <Field label={text.employee} required>
            <div className="app-overtime-employee">
              {view && <span>{view.employee}</span>}
              <AppButton
                {...restore}
                icon={<PersonSearch20Regular />}
                appearance="subtle"
                onClick={form.openPicker}
                disabled={!form.canChooseEmployee}
              >
                {view ? text.changeEmployee : text.chooseEmployee}
              </AppButton>
            </div>
          </Field>
        </div>
      )}
      {state.stage === "loading" && <AppLoading label={text.loadingReview} />}
      {state.stage === "submitting" && <AppLoading label={text.saving} />}
      {state.stage === "unavailable" && state.employeeId && (
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
            {text.saved}{" "}
            {props.savedTo && (
              <Link {...restore} to={props.savedTo}>
                {text.view}
              </Link>
            )}
          </MessageBarBody>
        </MessageBar>
      )}
      {view && (
        <>
          <AppPropertyList title={text.review} items={view.properties} />
          <form
            className="app-editor-form"
            aria-label={text.title}
            onSubmit={form.submit}
            noValidate
          >
            <fieldset className="app-editor-section app-report-content">
              <legend>
                {text.requestedTime} · {state.review?.timezone}
              </legend>
              <div className="app-editor-fields">
                <AppTextField
                  label={text.startsAt}
                  type="datetime-local"
                  step={60}
                  required
                  {...form.startsAt}
                  readOnly={!form.editable}
                />
                <AppTextField
                  label={text.endsAt}
                  type="datetime-local"
                  step={60}
                  required
                  {...form.endsAt}
                  readOnly={!form.editable}
                />
                <AppTextField
                  label={text.breakMinutes}
                  type="number"
                  min={0}
                  max={120}
                  step={1}
                  required
                  {...form.breakMinutes}
                  readOnly={!form.editable}
                />
              </div>
              <p className="app-muted">{text.bounds}</p>
              {form.invalidLocalTime && (
                <MessageBar intent="error" layout="multiline" role="alert">
                  <MessageBarBody>{text.invalidLocalTime}</MessageBarBody>
                </MessageBar>
              )}
            </fieldset>
            <AppTextField
              label={text.reason}
              required
              maxLength={1000}
              {...form.reason}
              readOnly={!form.editable}
            />
            <p className="app-muted">{text.hint}</p>
            <div className="app-form-actions">
              <AppButton type="submit" appearance="primary" disabled={!form.editable}>
                {text.create}
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
