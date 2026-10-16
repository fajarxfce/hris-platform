import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import { Add20Regular, ArrowClockwise20Regular, Delete20Regular } from "@fluentui/react-icons";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCheckbox } from "../../../../core/presentation/components/app-checkbox";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useCompanyMembershipForm } from "../controllers/use-company-membership-form";
import { companyMembershipMessages } from "../i18n/company-membership-messages";
import type { CompanyMembershipEditorState } from "../models/company-membership-editor-state";
import { RolePermissions } from "../widgets/role-permissions";

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
            <section className="app-panel" aria-label={text.roles}>
              <div className="app-member-role-heading">
                <h2>{text.roles}</h2>
                <AppButton
                  {...restore}
                  icon={<Add20Regular />}
                  disabled={!form.canAddRole}
                  onClick={() => form.openPicker(null)}
                >
                  {text.addRole}
                </AppButton>
              </div>
              <p>
                {text.roleHint} {text.limit}
              </p>
              {form.requiresRoleReview && <p role="alert">{text.reviewRequired}</p>}
              {form.roleViews.length === 0 && <p>{text.noRoles}</p>}
              {form.roleViews.map((role) => (
                <section className="app-member-role" key={role.id} aria-label={role.name}>
                  <div className="app-member-role-heading">
                    <div>
                      <h3 className="app-member-role-name">
                        {role.name} ({role.code})
                      </h3>
                      <p className="app-member-role-version">{role.versionLabel}</p>
                    </div>
                    <div className="app-form-actions">
                      <AppButton
                        {...restore}
                        appearance="subtle"
                        disabled={!form.editable}
                        onClick={() => form.openPicker(role.id)}
                      >
                        {text.replace}
                      </AppButton>
                      <AppButton
                        {...restore}
                        appearance="subtle"
                        icon={<Delete20Regular />}
                        disabled={!form.editable}
                        aria-label={`${text.remove}: ${role.name}`}
                        onClick={() => form.removeRole(role.id)}
                      />
                    </div>
                  </div>
                  <details>
                    <summary>{role.permissionsLabel}</summary>
                    <RolePermissions
                      title={text.permissions}
                      empty={text.none}
                      groups={role.groups}
                    />
                  </details>
                </section>
              ))}
              {state.failure?.fields.roleTemplates && <p role="alert">{text.invalidRoles}</p>}
            </section>
            <RolePermissions
              title={text.direct}
              empty={text.none}
              groups={form.groups}
              editable={form.editable}
              onSelect={form.selectPermission}
            />
            {state.failure?.fields.permissions && <p role="alert">{text.invalidPermissions}</p>}
            <RolePermissions
              title={text.effective}
              empty={text.none}
              groups={form.effectiveGroups}
            />
            <AppTextArea
              label={text.reason}
              {...form.reason}
              maxLength={1000}
              required
              readOnly={!form.editable}
              error={state.failure?.fields.reason ? text.invalid : undefined}
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
