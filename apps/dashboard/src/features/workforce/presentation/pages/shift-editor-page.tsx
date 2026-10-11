import { MessageBar, MessageBarBody, Text } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { workModes } from "../../domain/entities/shift-definition";
import type { useShiftForm } from "../controllers/use-shift-form";
import { shiftMessages } from "../i18n/shift-messages";
import type { ShiftEditorState } from "../models/shift-editor-state";

export function ShiftEditorPage(props: {
  state: ShiftEditorState;
  form: ReturnType<typeof useShiftForm>;
  creating: boolean;
  companyName: string;
  locale: Locale;
  backTo: string;
  savedTo: string | null;
  onRetry: () => void;
}) {
  const { state, form, locale } = props;
  const text = shiftMessages(locale);
  return (
    <section aria-busy={state.stage === "loading" || state.stage === "saving"}>
      <AppPageHeader
        title={props.creating ? text.create : text.edit}
        context={`${props.companyName} / ${text.title}`}
        actions={<Link to={props.backTo}>{text.back}</Link>}
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.stage === "unavailable" && (
        <AppButton onClick={form.refresh}>{messages(locale).retry}</AppButton>
      )}
      {state.stage === "unconfirmed" && (
        <MessageBar intent="warning" role="status">
          <MessageBarBody>
            {text.unconfirmed}
            <div className="app-reference">
              {text.operation}: {state.operationId}
            </div>
            <AppButton onClick={props.onRetry}>{text.retrySave}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "saved" && (
        <MessageBar intent="success" role="status">
          <MessageBarBody>
            {text.saved} {props.savedTo && <Link to={props.savedTo}>{text.view}</Link>}
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage !== "loading" && state.stage !== "unavailable" && (
        <form className="app-editor-form" onSubmit={form.submit}>
          <Text>{text.snapshot}</Text>
          <div className="app-editor-fields">
            <AppTextField
              label={text.code}
              {...form.code}
              maxLength={32}
              required
              readOnly={!form.editable}
            />
            <AppTextField
              label={text.name}
              {...form.name}
              maxLength={200}
              required
              readOnly={!form.editable}
            />
            <AppTextField
              label={text.startsAt}
              {...form.startsAt}
              type="time"
              required
              readOnly={!form.editable}
            />
            <AppTextField
              label={text.endsAt}
              {...form.endsAt}
              type="time"
              required
              readOnly={!form.editable}
              hint={text.overnight}
            />
            <AppTextField
              label={text.breakMinutes}
              {...form.breakMinutes}
              type="number"
              min={0}
              max={1439}
              step={1}
              required
              readOnly={!form.editable}
            />
            <AppTextField
              label={text.timezone}
              {...form.timezone}
              maxLength={128}
              required
              readOnly={!form.editable}
              hint="Asia/Jakarta"
            />
            <AppSelect label={text.mode} {...form.mode} disabled={!form.editable}>
              {workModes.map((mode) => (
                <option key={mode} value={mode}>
                  {text[mode]}
                </option>
              ))}
            </AppSelect>
            <AppSelect label={text.status} {...form.active} disabled={!form.editable}>
              <option value="true">{text.active}</option>
              <option value="false">{text.inactive}</option>
            </AppSelect>
          </div>
          <fieldset className="app-editor-fields">
            <legend>{text.location}</legend>
            <AppSelect
              label={text.locationRequired}
              {...form.locationRequired}
              disabled={!form.editable}
            >
              <option value="false">{text.no}</option>
              <option value="true">{text.yes}</option>
            </AppSelect>
            <AppTextField
              label={text.maxAccuracy}
              {...form.maxAccuracyMeters}
              type="number"
              min={1}
              max={10000}
              step="any"
              required
              readOnly={!form.editable}
              hint={text.accuracyHint}
            />
            {form.locationRequired.value === "true" && (
              <AppSelect label={text.fenced} {...form.fenced} disabled={!form.editable}>
                <option value="false">{text.no}</option>
                <option value="true">{text.yes}</option>
              </AppSelect>
            )}
            {form.locationRequired.value === "true" && form.fenced.value === "true" && (
              <>
                <AppTextField
                  label={text.latitude}
                  {...form.latitude}
                  type="number"
                  min={-90}
                  max={90}
                  step="any"
                  required
                  readOnly={!form.editable}
                />
                <AppTextField
                  label={text.longitude}
                  {...form.longitude}
                  type="number"
                  min={-180}
                  max={180}
                  step="any"
                  required
                  readOnly={!form.editable}
                />
                <AppTextField
                  label={text.radius}
                  {...form.radiusMeters}
                  type="number"
                  min={1}
                  max={100000}
                  step="any"
                  required
                  readOnly={!form.editable}
                />
              </>
            )}
          </fieldset>
          <AppTextArea
            label={text.reason}
            {...form.reason}
            maxLength={1000}
            required
            readOnly={!form.editable}
          />
          {state.stage === "saving" && <AppLoading label={text.saving} />}
          <div className="app-form-actions">
            <AppButton type="submit" appearance="primary" disabled={!form.editable || !form.dirty}>
              {text.save}
            </AppButton>
            {(state.stage === "editing" || state.stage === "conflict") && !props.creating && (
              <AppButton onClick={form.refresh}>{text.reload}</AppButton>
            )}
          </div>
        </form>
      )}
    </section>
  );
}
