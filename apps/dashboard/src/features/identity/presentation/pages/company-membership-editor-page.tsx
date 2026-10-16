import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCheckbox } from "../../../../core/presentation/components/app-checkbox";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useCompanyMembershipForm } from "../controllers/use-company-membership-form";
import { companyMembershipMessages } from "../i18n/company-membership-messages";
import type { CompanyMembershipEditorState } from "../models/company-membership-editor-state";
import { MembershipAccessFields } from "../widgets/membership-access-fields";

export function CompanyMembershipEditorPage({
  state,
  form,
  companyName,
  locale,
  backTo,
  onRetry,
  picker,
}: {
  state: CompanyMembershipEditorState;
  form: ReturnType<typeof useCompanyMembershipForm>;
  companyName: string;
  locale: Locale;
  backTo: string;
  onRetry: () => void;
  picker: ReactNode;
}) {
  const text = companyMembershipMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <section
      className="app-report-content app-role-editor"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={text.title}
        context={companyName}
        actions={
          <>
            <Link {...restore} to={backTo}>
              {text.back}
            </Link>
            <AppButton
              {...restore}
              appearance="subtle"
              icon={<ArrowClockwise20Regular />}
              aria-label={messages(locale).refresh}
              disabled={
                state.stage === "loading" ||
                state.stage === "saving" ||
                state.stage === "unconfirmed"
              }
              onClick={form.refresh}
            />
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.stage === "saving" && <AppLoading label={text.saving} />}
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
            {text.saved} {text.version}: {state.receipt?.version}.{" "}
            <Link {...restore} to={backTo}>
              {text.view}
            </Link>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.grant && (
        <>
          <div className="app-panel app-member-summary">
            <h2>{state.grant.member.displayName}</h2>
            <p>{state.grant.member.email}</p>
            <p>
              {text.basedOn}: {state.grant.member.version}
            </p>
          </div>
          {!state.grant.member.accountActive && (
            <MessageBar layout="multiline" intent="warning">
              <MessageBarBody>{text.inactiveAccount}</MessageBarBody>
            </MessageBar>
          )}
          <form className="app-editor-form" onSubmit={form.submit} noValidate>
            <AppCheckbox
              label={text.active}
              checked={form.active.value}
              ref={form.active.ref}
              onBlur={form.active.onBlur}
              disabled={!form.editable}
              onChange={(_, data) => form.active.onChange(data.checked === true)}
            />
            <MembershipAccessFields
              form={form}
              failure={state.failure}
              roleHint={text.roleHint}
              locale={locale}
            />
            <div className="app-form-actions">
              <AppButton appearance="primary" type="submit" disabled={!form.canSave}>
                {text.save}
              </AppButton>
              {state.stage === "conflict" && (
                <AppButton onClick={form.refresh}>{text.reload}</AppButton>
              )}
            </div>
          </form>
        </>
      )}
      {picker}
    </section>
  );
}
