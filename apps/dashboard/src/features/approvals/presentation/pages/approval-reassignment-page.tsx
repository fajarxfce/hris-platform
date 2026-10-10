import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useApprovalReassignmentForm } from "../controllers/use-approval-reassignment-form";
import { approvalReassignmentMessages } from "../i18n/approval-reassignment-messages";
import { approvalTemplateMessages } from "../i18n/approval-template-messages";
import type { ApprovalReassignmentState } from "../models/approval-reassignment-state";
import type { approvalReassignmentView } from "../models/approval-reassignment-view";

export function ApprovalReassignmentPage({
  state,
  form,
  properties,
  companyName,
  locale,
  backTo,
  onRetry,
}: {
  state: ApprovalReassignmentState;
  form: ReturnType<typeof useApprovalReassignmentForm>;
  properties: ReturnType<typeof approvalReassignmentView> | null;
  companyName: string;
  locale: Locale;
  backTo: string;
  onRetry: () => void;
}) {
  const text = approvalReassignmentMessages(locale);
  const common = approvalTemplateMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <section
      className="app-report-content app-approval-reassignment"
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
            <Link {...restore} to={backTo}>
              {text.view}
            </Link>
          </MessageBarBody>
        </MessageBar>
      )}
      {properties && <AppPropertyList title={text.review} items={properties} />}
      {state.request && (
        <form className="app-editor-form" onSubmit={form.submit} noValidate>
          <h2>{text.selection}</h2>
          <p>{text.hint}</p>
          <p>{text.exclusions}</p>
          <ul className="app-approval-accounts">
            {form.accounts.map((account) => (
              <li key={account.id}>
                <span className="app-reference">{account.displayName}</span>
                <AppButton
                  disabled={!form.editable}
                  onClick={() => form.remove(account.id)}
                  aria-label={`${common.removeApprover}: ${account.displayName}`}
                >
                  {common.removeApprover}
                </AppButton>
              </li>
            ))}
          </ul>
          <div className="app-form-actions">
            <AppButton
              {...restore}
              disabled={!form.editable || form.accounts.length >= 25}
              onClick={form.openPicker}
            >
              {common.addApprover}
            </AppButton>
          </div>
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
            {(state.stage === "editing" || state.stage === "conflict") && (
              <AppButton {...restore} onClick={form.refresh}>
                {common.reload}
              </AppButton>
            )}
          </div>
        </form>
      )}
    </section>
  );
}
