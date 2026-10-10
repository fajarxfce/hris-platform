import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCheckbox } from "../../../../core/presentation/components/app-checkbox";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useClientPolicyEditorForm } from "../controllers/use-client-policy-editor-form";
import {
  clientPolicyEditorMessages,
  clientPolicyFieldMessage,
} from "../i18n/client-policy-editor-messages";
import { clientPolicyMessages } from "../i18n/client-policy-messages";
import type { ClientPolicyEditorState } from "../models/client-policy-editor-state";

export function ClientPolicyEditorPage({
  state,
  form,
  companyName,
  locale,
  clientBuild,
  backTo,
  savedTo,
  onRetry,
}: {
  state: ClientPolicyEditorState;
  form: ReturnType<typeof useClientPolicyEditorForm>;
  companyName: string;
  locale: Locale;
  clientBuild: number;
  backTo: string;
  savedTo: string | null;
  onRetry: () => void;
}) {
  const text = clientPolicyEditorMessages(locale);
  const policy = clientPolicyMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <section
      className="app-report-content"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={text.title}
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
        <MessageBar intent="warning" role="status">
          <MessageBarBody>
            {text.unconfirmed}
            <div className="app-reference">
              {text.operation}: {state.operationId}
            </div>
            <AppButton onClick={onRetry}>{text.retry}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "saved" && savedTo && (
        <MessageBar intent="success" role="status">
          <MessageBarBody>
            {text.saved}{" "}
            <Link {...restore} to={savedTo}>
              {text.view}
            </Link>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.settings && (
        <>
          <p>
            {text.basedOn}: {state.settings.latest?.version ?? text.default} · {text.effective}:{" "}
            {state.settings.effective.version ?? text.default}
            {" · "}
            {text.clientBuild}: {clientBuild}
          </p>
          <form className="app-editor-form" onSubmit={form.submit} noValidate>
            <div className="app-editor-fields">
              <AppSelect label={text.activation} {...form.activation} disabled={!form.editable}>
                <option value="IMMEDIATE">{text.immediate}</option>
                <option value="SCHEDULED">{text.scheduled}</option>
              </AppSelect>
              {form.activation.value === "SCHEDULED" && (
                <AppTextField
                  label={text.activateAt}
                  {...form.activateAt}
                  placeholder="2027-01-01T09:00:00Z"
                  required
                  readOnly={!form.editable}
                  maxLength={40}
                  error={clientPolicyFieldMessage(state.failure, "activateAt", locale)}
                />
              )}
            </div>
            <p>{text.scheduleHint}</p>
            <fieldset className="app-editor-section">
              <legend>{text.enabledModules}</legend>
              <p>{text.moduleHint}</p>
              <div className="app-editor-fields">
                {form.modules.map((module) => (
                  <AppCheckbox
                    key={module.key}
                    label={policy[module.key]}
                    checked={module.enabled}
                    disabled={!form.editable}
                    onChange={(_, data) => form.setModule(module.key, data.checked === true)}
                  />
                ))}
              </div>
            </fieldset>
            <fieldset className="app-editor-section">
              <legend>{policy.minimumBuilds}</legend>
              <p>{text.buildHint}</p>
              <div className="app-editor-fields">
                <AppTextField
                  label="Android"
                  {...form.android}
                  type="number"
                  min={0}
                  max={999999999}
                  step={1}
                  required
                  readOnly={!form.editable}
                  error={clientPolicyFieldMessage(state.failure, "minimumBuilds.android", locale)}
                />
                <AppTextField
                  label="iOS"
                  {...form.ios}
                  type="number"
                  min={0}
                  max={999999999}
                  step={1}
                  required
                  readOnly={!form.editable}
                  error={clientPolicyFieldMessage(state.failure, "minimumBuilds.ios", locale)}
                />
                <AppTextField
                  label="Web"
                  {...form.web}
                  type="number"
                  min={0}
                  max={999999999}
                  step={1}
                  required
                  readOnly={!form.editable}
                  error={clientPolicyFieldMessage(state.failure, "minimumBuilds.web", locale)}
                  hint={form.webRequiresUpdate ? text.webHint : undefined}
                />
              </div>
            </fieldset>
            <fieldset className="app-editor-section">
              <legend>{text.maintenanceSection}</legend>
              <p>{text.utcHint}</p>
              <AppCheckbox
                label={text.maintenance}
                checked={form.maintenanceEnabled.value}
                ref={form.maintenanceEnabled.ref}
                onBlur={form.maintenanceEnabled.onBlur}
                disabled={!form.editable}
                onChange={(_, data) => form.maintenanceEnabled.onChange(data.checked === true)}
              />
              {form.maintenanceEnabled.value && (
                <div className="app-editor-fields">
                  <AppTextField
                    label={text.maintenanceStarts}
                    {...form.maintenanceStarts}
                    placeholder="2027-01-01T09:00:00Z"
                    required
                    readOnly={!form.editable}
                    maxLength={40}
                    error={
                      clientPolicyFieldMessage(state.failure, "maintenance.startsAt", locale) ??
                      clientPolicyFieldMessage(state.failure, "maintenance", locale)
                    }
                  />
                  <AppTextField
                    label={text.maintenanceEnds}
                    {...form.maintenanceEnds}
                    placeholder="2027-01-01T09:30:00Z"
                    required
                    readOnly={!form.editable}
                    maxLength={40}
                    error={clientPolicyFieldMessage(state.failure, "maintenance.endsAt", locale)}
                  />
                </div>
              )}
            </fieldset>
            <AppTextField
              label={text.reason}
              {...form.reason}
              required
              maxLength={1000}
              readOnly={!form.editable}
              error={clientPolicyFieldMessage(state.failure, "reason", locale)}
            />
            {state.stage === "saving" && <AppLoading label={text.saving} />}
            <div className="app-form-actions">
              <AppButton appearance="primary" type="submit" disabled={!form.editable}>
                {text.save}
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
