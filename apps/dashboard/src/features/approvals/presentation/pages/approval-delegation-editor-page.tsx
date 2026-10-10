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
import { approvalKinds } from "../../domain/entities/approval-request";
import type { useApprovalDelegationForm } from "../controllers/use-approval-delegation-form";
import { approvalDelegationMessages } from "../i18n/approval-delegation-messages";
import { approvalMessages } from "../i18n/approval-messages";
import { approvalTemplateMessages } from "../i18n/approval-template-messages";
import type { ApprovalDelegationEditorState } from "../models/approval-delegation-editor-state";

export function ApprovalDelegationEditorPage({
  state,
  form,
  creating,
  canChooseFrom,
  companyName,
  timezone,
  locale,
  backTo,
  savedTo,
  onRetry,
}: {
  state: ApprovalDelegationEditorState;
  form: ReturnType<typeof useApprovalDelegationForm>;
  creating: boolean;
  canChooseFrom: boolean;
  companyName: string;
  timezone: string;
  locale: Locale;
  backTo: string;
  savedTo: string | null;
  onRetry: () => void;
}) {
  const text = approvalDelegationMessages(locale);
  const common = approvalTemplateMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <section
      className="app-report-content app-approval-delegation-editor"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={creating ? text.create : text.edit}
        context={`${companyName} / ${text.title}`}
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
            {common.unconfirmed}
            <div className="app-reference">
              {common.operation}: {state.operationId}
            </div>
            <AppButton onClick={onRetry}>{common.retry}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "saved" && (
        <MessageBar intent="success" layout="multiline" role="status">
          <MessageBarBody>
            {text.saved} {common.currentVersion}: {state.receipt?.version}.{" "}
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
          {state.delegation && (
            <p>
              {common.basedOn}: {state.delegation.version}
            </p>
          )}
          <p>{text.periodHint}</p>
          <form className="app-editor-form" onSubmit={form.submit} noValidate>
            <AppSelect
              label={approvalMessages(locale).kind}
              {...form.kind}
              disabled={!form.editable}
              onChange={(_event, data) => form.changeKind(data.value)}
            >
              {approvalKinds.map((kind) => (
                <option key={kind} value={kind}>
                  {approvalMessages(locale)[kind]}
                </option>
              ))}
            </AppSelect>
            <p>{text.kindHint}</p>
            <div className="app-editor-fields">
              <div className="app-editor-form">
                <AppTextField
                  label={text.from}
                  value={form.fromAccount.displayName}
                  readOnly
                  hint={text.chooseHint}
                />
                {canChooseFrom && (
                  <AppButton {...restore} disabled={!form.editable} onClick={form.chooseFrom}>
                    {text.chooseFrom}
                  </AppButton>
                )}
              </div>
              <div className="app-editor-form">
                <AppTextField label={text.to} value={form.toAccount?.displayName ?? ""} readOnly />
                <AppButton {...restore} disabled={!form.editable} onClick={form.chooseTo}>
                  {text.chooseTo}
                </AppButton>
              </div>
            </div>
            <p>
              {text.zoneHint}: {timezone}.
            </p>
            <div className="app-editor-fields">
              <AppTextField
                label={text.starts}
                {...form.validFrom}
                type="datetime-local"
                step={1}
                required
                readOnly={!form.editable}
                error={form.invalidFrom ? text.invalidTime : undefined}
              />
              <AppTextField
                label={text.ends}
                {...form.validUntil}
                type="datetime-local"
                step={1}
                required
                readOnly={!form.editable}
                error={form.invalidUntil ? text.invalidTime : undefined}
              />
            </div>
            <AppCheckbox
              label={text.enabled}
              checked={form.active.value}
              ref={form.active.ref}
              onBlur={form.active.onBlur}
              disabled={!form.editable}
              onChange={(_, data) => form.active.onChange(data.checked === true)}
            />
            <AppTextField
              label={common.reason}
              {...form.reason}
              maxLength={1000}
              required
              readOnly={!form.editable}
            />
            {state.stage === "saving" && <AppLoading label={text.saving} />}
            <div className="app-form-actions">
              <AppButton appearance="primary" type="submit" disabled={!form.editable}>
                {text.save}
              </AppButton>
              {!creating && (state.stage === "editing" || state.stage === "conflict") && (
                <AppButton {...restore} onClick={form.refresh}>
                  {common.reload}
                </AppButton>
              )}
            </div>
          </form>
        </>
      )}
    </section>
  );
}
