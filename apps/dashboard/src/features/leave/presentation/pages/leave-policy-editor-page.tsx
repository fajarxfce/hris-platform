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
import type { useLeavePolicyForm } from "../controllers/use-leave-policy-form";
import { leaveMessages } from "../i18n/leave-messages";
import {
  leavePolicyEditorMessages,
  leavePolicyFieldError,
} from "../i18n/leave-policy-editor-messages";
import { leavePolicyMessages } from "../i18n/leave-policy-messages";
import type { LeavePolicyEditorState } from "../models/leave-policy-editor-state";

export function LeavePolicyEditorPage({
  state,
  form,
  creating,
  companyName,
  locale,
  backTo,
  savedTo,
  onRetry,
}: {
  state: LeavePolicyEditorState;
  form: ReturnType<typeof useLeavePolicyForm>;
  creating: boolean;
  companyName: string;
  locale: Locale;
  backTo: string;
  savedTo: string | null;
  onRetry: () => void;
}) {
  const text = leavePolicyEditorMessages(locale);
  const policy = leavePolicyMessages(locale);
  const leave = leaveMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <section
      className="app-report-content"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={creating ? text.create : text.edit}
        context={`${companyName} / ${policy.title}`}
        actions={
          <Link {...restore} to={backTo}>
            {policy.back}
          </Link>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.stage === "unavailable" && (
        <AppButton onClick={form.refresh}>{messages(locale).retry}</AppButton>
      )}
      {state.stage === "unconfirmed" && (
        <MessageBar layout="multiline" intent="warning" role="status">
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
        <MessageBar layout="multiline" intent="success" role="status">
          <MessageBarBody>
            {text.saved} {text.currentVersion}: {state.receipt?.version}.{" "}
            {savedTo && (
              <Link {...restore} to={savedTo}>
                {text.view}
              </Link>
            )}
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage !== "loading" && state.stage !== "unavailable" && state.stage !== "saved" && (
        <>
          {state.policy && (
            <p>
              {text.basedOn}: {state.policy.version}
            </p>
          )}
          <form className="app-editor-form" onSubmit={form.submit} noValidate>
            <div className="app-editor-fields">
              <AppTextField
                label={policy.code}
                {...form.code}
                maxLength={32}
                required
                readOnly={!creating || !form.editable}
                hint={text.codeHint}
                error={leavePolicyFieldError(state.failure?.fields.code, locale)}
              />
              <AppTextField
                label={policy.name}
                {...form.name}
                maxLength={200}
                required
                readOnly={!form.editable}
                error={leavePolicyFieldError(state.failure?.fields.name, locale)}
              />
              <AppTextField
                label={policy.from}
                {...form.effectiveFrom}
                type="date"
                min="1900-01-01"
                max="2200-12-31"
                required
                readOnly={!form.editable}
                error={leavePolicyFieldError(state.failure?.fields.effectiveFrom, locale)}
              />
            </div>
            <AppCheckbox
              label={policy.enabled}
              checked={form.active.value}
              ref={form.active.ref}
              onBlur={form.active.onBlur}
              disabled={!form.editable}
              onChange={(_, data) => form.active.onChange(data.checked === true)}
            />
            <h2>{policy.terms}</h2>
            <div className="app-editor-fields">
              <AppCheckbox
                label={leave.paid}
                checked={form.paid.value}
                ref={form.paid.ref}
                onBlur={form.paid.onBlur}
                disabled={!form.editable}
                onChange={(_, data) => form.paid.onChange(data.checked === true)}
              />
              <AppCheckbox
                label={leave.partial}
                checked={form.allowPartialDays.value}
                ref={form.allowPartialDays.ref}
                onBlur={form.allowPartialDays.onBlur}
                disabled={!form.editable}
                onChange={(_, data) => form.allowPartialDays.onChange(data.checked === true)}
              />
              <AppCheckbox
                label={leave.required}
                checked={form.attachmentRequired.value}
                ref={form.attachmentRequired.ref}
                onBlur={form.attachmentRequired.onBlur}
                disabled={!form.editable}
                onChange={(_, data) => form.attachmentRequired.onChange(data.checked === true)}
              />
            </div>
            <div className="app-editor-fields">
              <AppTextField
                label={leave.months}
                {...form.minServiceMonths}
                inputMode="numeric"
                maxLength={3}
                required
                readOnly={!form.editable}
                hint="0–120"
                error={leavePolicyFieldError(state.failure?.fields.minServiceMonths, locale)}
              />
              <AppTextField
                label={leave.maximum}
                {...form.maxRequestDays}
                inputMode="numeric"
                maxLength={3}
                required
                readOnly={!form.editable}
                hint="1–366"
                error={leavePolicyFieldError(state.failure?.fields.maxRequestDays, locale)}
              />
            </div>
            <fieldset className="app-editor-fields">
              <legend>{text.contracts}</legend>
              <AppCheckbox
                label={leave.PERMANENT}
                checked={form.permanent.value}
                ref={form.permanent.ref}
                onBlur={form.permanent.onBlur}
                disabled={!form.editable}
                onChange={(_, data) => form.permanent.onChange(data.checked === true)}
              />
              <AppCheckbox
                label={leave.FIXED_TERM}
                checked={form.fixedTerm.value}
                ref={form.fixedTerm.ref}
                onBlur={form.fixedTerm.onBlur}
                disabled={!form.editable}
                onChange={(_, data) => form.fixedTerm.onChange(data.checked === true)}
              />
              {state.failure?.fields.allowedContracts && (
                <p role="alert">
                  {leavePolicyFieldError(state.failure.fields.allowedContracts, locale)}
                </p>
              )}
            </fieldset>
            <h2>{policy.accrual}</h2>
            <p>{text.accrualHint}</p>
            <AppSelect label={policy.accrual} {...form.frequency} disabled={!form.editable}>
              <option value="NONE">{policy.noAccrual}</option>
              <option value="MANUAL">{policy.MANUAL}</option>
              <option value="MONTHLY">{policy.MONTHLY}</option>
              <option value="ANNUAL">{policy.ANNUAL}</option>
            </AppSelect>
            {form.frequency.value !== "NONE" && (
              <>
                <p>{text.daysHint}</p>
                <div className="app-editor-fields">
                  {(form.frequency.value === "MONTHLY" || form.frequency.value === "ANNUAL") && (
                    <AppTextField
                      label={policy.perPeriod}
                      {...form.daysPerPeriod}
                      inputMode="decimal"
                      maxLength={5}
                      required
                      readOnly={!form.editable}
                      hint={form.frequency.value === "MONTHLY" ? text.monthlyHint : text.annualHint}
                      error={leavePolicyFieldError(
                        state.failure?.fields["accrual.daysPerPeriod"],
                        locale,
                      )}
                    />
                  )}
                  <AppTextField
                    label={policy.carryLimit}
                    {...form.carryLimitDays}
                    inputMode="decimal"
                    maxLength={5}
                    required
                    readOnly={!form.editable}
                    hint="0–366"
                    error={leavePolicyFieldError(
                      state.failure?.fields["accrual.carryLimitDays"],
                      locale,
                    )}
                  />
                </div>
              </>
            )}
            <AppTextField
              label={policy.reason}
              {...form.reason}
              maxLength={1000}
              required
              readOnly={!form.editable}
              error={leavePolicyFieldError(state.failure?.fields.reason, locale)}
            />
            <div className="app-form-actions">
              <AppButton appearance="primary" type="submit" disabled={!form.editable}>
                {text.save}
              </AppButton>
              {state.stage === "conflict" && (
                <AppButton onClick={form.refresh}>{text.reload}</AppButton>
              )}
            </div>
          </form>
        </>
      )}
    </section>
  );
}
