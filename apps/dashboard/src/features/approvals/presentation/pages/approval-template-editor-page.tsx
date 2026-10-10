import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import type { ReactNode } from "react";
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
import type { useApprovalTemplateForm } from "../controllers/use-approval-template-form";
import { approvalMessages } from "../i18n/approval-messages";
import { approvalTemplateMessages } from "../i18n/approval-template-messages";
import type { ApprovalTemplateEditorState } from "../models/approval-template-editor-state";

export function ApprovalTemplateEditorPage({
  state,
  form,
  creating,
  companyName,
  locale,
  backTo,
  savedTo,
  children,
  onRetry,
}: {
  state: ApprovalTemplateEditorState;
  form: ReturnType<typeof useApprovalTemplateForm>;
  creating: boolean;
  companyName: string;
  locale: Locale;
  backTo: string;
  savedTo: string | null;
  children: ReactNode;
  onRetry: () => void;
}) {
  const text = approvalTemplateMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <section
      className="app-report-content app-approval-template-editor"
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
          {state.template && (
            <p>
              {text.basedOn}: {state.template.version}
            </p>
          )}
          <p>{text.createHint}</p>
          <form className="app-editor-form" onSubmit={form.submit} noValidate>
            <div className="app-editor-fields">
              <AppTextField
                label={text.name}
                {...form.name}
                maxLength={200}
                required
                readOnly={!form.editable}
              />
              {creating ? (
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
              ) : (
                <AppTextField
                  label={approvalMessages(locale).kind}
                  value={approvalMessages(locale)[form.kind.value]}
                  readOnly
                />
              )}
              <AppCheckbox
                label={text.active}
                checked={form.active.value}
                ref={form.active.ref}
                onBlur={form.active.onBlur}
                disabled={!form.editable}
                onChange={(_, data) => form.active.onChange(data.checked === true)}
              />
            </div>
            {creating && <p>{text.kindHint}</p>}
            <div className="app-editor-fields">
              <AppTextField
                label={text.effectiveFrom}
                {...form.effectiveFrom}
                type="date"
                min="1900-01-01"
                max="2200-12-31"
                required
                readOnly={!form.editable}
              />
              <AppTextField
                label={text.category}
                {...form.category}
                maxLength={80}
                hint={text.categoryHint}
                readOnly={!form.editable}
              />
              <AppTextField
                label={text.minimumAmount}
                {...form.minimumAmount}
                inputMode="decimal"
                maxLength={21}
                required
                hint={text.amountHint}
                readOnly={!form.editable}
              />
            </div>
            <h2>{approvalMessages(locale).stages}</h2>
            <p>{text.stageHint}</p>
            {children}
            <div className="app-form-actions">
              <AppButton
                onClick={form.addStage}
                disabled={!form.editable || form.stages.length >= 8}
              >
                {text.addStage}
              </AppButton>
            </div>
            <AppTextField
              label={text.reason}
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
