import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCheckbox } from "../../../../core/presentation/components/app-checkbox";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useExpensePolicyForm } from "../controllers/use-expense-policy-form";
import {
  expensePolicyEditorMessages,
  expensePolicyFieldError,
} from "../i18n/expense-policy-editor-messages";
import { expensePolicyMessages } from "../i18n/expense-policy-messages";
import type { ExpensePolicyEditorState } from "../models/expense-policy-editor-state";

export function ExpensePolicyEditorPage({
  state,
  form,
  creating,
  companyName,
  locale,
  backTo,
  savedTo,
  onRetry,
}: {
  state: ExpensePolicyEditorState;
  form: ReturnType<typeof useExpensePolicyForm>;
  creating: boolean;
  companyName: string;
  locale: Locale;
  backTo: string;
  savedTo: string | null;
  onRetry: () => void;
}) {
  const text = expensePolicyEditorMessages(locale),
    policy = expensePolicyMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <section
      className="app-report-content app-record-editor app-expense-policy-editor"
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
      {state.stage === "saving" && <AppLoading label={text.save} />}
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
            <h2>{text.identity}</h2>
            <div className="app-editor-fields">
              <AppTextField
                label={policy.code}
                {...form.code}
                maxLength={32}
                required
                readOnly={!creating || !form.editable}
                hint={text.codeHint}
                error={expensePolicyFieldError(state.failure?.fields.code, locale)}
              />
              <AppTextField
                label={policy.name}
                {...form.name}
                maxLength={120}
                required
                readOnly={!form.editable}
                error={expensePolicyFieldError(state.failure?.fields.name, locale)}
              />
              <AppTextField
                label={policy.from}
                {...form.effectiveFrom}
                type="date"
                min="1900-01-01"
                max="2200-12-31"
                required
                readOnly={!form.editable}
                error={expensePolicyFieldError(state.failure?.fields.effectiveFrom, locale)}
              />
              <AppCheckbox
                label={policy.enabled}
                checked={form.active.value}
                ref={form.active.ref}
                onBlur={form.active.onBlur}
                disabled={!form.editable}
                onChange={(_, data) => form.active.onChange(data.checked === true)}
              />
            </div>
            <p className="app-muted">{text.archiveHint}</p>
            <h2>{text.limits}</h2>
            <div className="app-editor-fields">
              <AppTextField
                label={policy.maximumLine}
                {...form.maximumLineAmount}
                inputMode="decimal"
                maxLength={15}
                required
                readOnly={!form.editable}
                hint={text.amountHint}
                error={expensePolicyFieldError(state.failure?.fields.maximumLineAmount, locale)}
              />
              <AppTextField
                label={policy.maximumClaim}
                {...form.maximumClaimAmount}
                inputMode="decimal"
                maxLength={15}
                required
                readOnly={!form.editable}
                hint={text.amountHint}
                error={expensePolicyFieldError(state.failure?.fields.maximumClaimAmount, locale)}
              />
              <AppTextField
                label={policy.maximumAge}
                {...form.maximumAgeDays}
                inputMode="numeric"
                maxLength={3}
                required
                readOnly={!form.editable}
                hint="1–366"
                error={expensePolicyFieldError(state.failure?.fields.maximumAgeDays, locale)}
              />
            </div>
            <h2>{policy.terms}</h2>
            <div className="app-editor-fields">
              <AppCheckbox
                label={policy.receipt}
                checked={form.receiptRequired.value}
                ref={form.receiptRequired.ref}
                onBlur={form.receiptRequired.onBlur}
                disabled={!form.editable}
                onChange={(_, data) => form.receiptRequired.onChange(data.checked === true)}
              />
              <AppCheckbox
                label={policy.costCenter}
                checked={form.costCenterRequired.value}
                ref={form.costCenterRequired.ref}
                onBlur={form.costCenterRequired.onBlur}
                disabled={!form.editable}
                onChange={(_, data) => form.costCenterRequired.onChange(data.checked === true)}
              />
            </div>
            <fieldset className="app-editor-fields">
              <legend>{policy.contracts}</legend>
              <AppCheckbox
                label={policy.PERMANENT}
                checked={form.permanent.value}
                ref={form.permanent.ref}
                onBlur={form.permanent.onBlur}
                disabled={!form.editable}
                onChange={(_, data) => form.permanent.onChange(data.checked === true)}
              />
              <AppCheckbox
                label={policy.FIXED_TERM}
                checked={form.fixedTerm.value}
                ref={form.fixedTerm.ref}
                onBlur={form.fixedTerm.onBlur}
                disabled={!form.editable}
                onChange={(_, data) => form.fixedTerm.onChange(data.checked === true)}
              />
              {state.failure?.fields.allowedContracts && (
                <p role="alert">
                  {expensePolicyFieldError(state.failure.fields.allowedContracts, locale)}
                </p>
              )}
            </fieldset>
            <AppTextField
              label={policy.reason}
              {...form.reason}
              maxLength={1000}
              required
              readOnly={!form.editable}
              error={expensePolicyFieldError(state.failure?.fields.reason, locale)}
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
