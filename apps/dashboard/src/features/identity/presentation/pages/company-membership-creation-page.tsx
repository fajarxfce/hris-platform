import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useCompanyMembershipCreationForm } from "../controllers/use-company-membership-creation-form";
import { companyMembershipCreationMessages } from "../i18n/company-membership-creation-messages";
import { companyMembershipMessages } from "../i18n/company-membership-messages";
import type { CompanyMembershipCreationState } from "../models/company-membership-creation-state";
import { MembershipAccessFields } from "../widgets/membership-access-fields";

export function CompanyMembershipCreationPage({
  state,
  form,
  companyName,
  locale,
  backTo,
  memberTo,
  onRetry,
  picker,
}: {
  state: CompanyMembershipCreationState;
  form: ReturnType<typeof useCompanyMembershipCreationForm>;
  companyName: string;
  locale: Locale;
  backTo: string;
  memberTo: string;
  onRetry: () => void;
  picker: ReactNode;
}) {
  const text = companyMembershipCreationMessages(locale);
  const membership = companyMembershipMessages(locale);
  return (
    <section
      className="app-report-content app-role-editor"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={text.review}
        context={companyName}
        actions={<Link to={backTo}>{text.changeAccount}</Link>}
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.stage === "saving" && <AppLoading label={membership.saving} />}
      {state.stage === "unavailable" &&
        (state.failure?.code === "company_member_already_exists" ? (
          <Link to={memberTo}>{text.existing}</Link>
        ) : (
          <AppButton onClick={form.refresh}>{messages(locale).retry}</AppButton>
        ))}
      {state.stage === "unconfirmed" && (
        <MessageBar layout="multiline" intent="warning" role="status">
          <MessageBarBody>
            {membership.unconfirmed}
            <div className="app-reference">
              {membership.operation}: {state.operationId}
            </div>
            <AppButton onClick={onRetry}>{membership.retry}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "saved" && (
        <MessageBar layout="multiline" intent="success" role="status">
          <MessageBarBody>
            {membership.saved} <Link to={memberTo}>{membership.view}</Link>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.candidate && (
        <>
          <div className="app-panel app-member-summary">
            <h2>{state.candidate.displayName}</h2>
            <p>{state.candidate.email}</p>
          </div>
          <p>{text.scope}</p>
          <form className="app-editor-form" onSubmit={form.submit} noValidate>
            <MembershipAccessFields
              form={form}
              failure={state.failure}
              roleHint={text.roles}
              locale={locale}
            />
            <div className="app-form-actions">
              <AppButton appearance="primary" type="submit" disabled={!form.canSave}>
                {text.save}
              </AppButton>
              {state.stage === "conflict" && (
                <AppButton onClick={form.refresh}>{membership.reload}</AppButton>
              )}
            </div>
          </form>
        </>
      )}
      {picker}
    </section>
  );
}
