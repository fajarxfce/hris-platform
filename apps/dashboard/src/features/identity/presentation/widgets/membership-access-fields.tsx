import { useRestoreFocusTarget } from "@fluentui/react-components";
import { Add20Regular, Delete20Regular } from "@fluentui/react-icons";
import type { Failure } from "../../../../core/domain/result";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { useMembershipAccessForm } from "../controllers/use-membership-access-form";
import { companyMembershipMessages } from "../i18n/company-membership-messages";
import { RolePermissions } from "./role-permissions";

export function MembershipAccessFields({
  form,
  failure,
  roleHint,
  locale,
}: {
  form: ReturnType<typeof useMembershipAccessForm>;
  failure: Failure | null;
  roleHint: string;
  locale: Locale;
}) {
  const text = companyMembershipMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <>
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
          {roleHint} {text.limit}
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
              <RolePermissions title={text.permissions} empty={text.none} groups={role.groups} />
            </details>
          </section>
        ))}
        {failure?.fields.roleTemplates && <p role="alert">{text.invalidRoles}</p>}
      </section>
      <RolePermissions
        title={text.direct}
        empty={text.none}
        groups={form.groups}
        editable={form.editable}
        onSelect={form.selectPermission}
      />
      {failure?.fields.permissions && <p role="alert">{text.invalidPermissions}</p>}
      <RolePermissions title={text.effective} empty={text.none} groups={form.effectiveGroups} />
      <AppTextArea
        label={text.reason}
        {...form.reason}
        maxLength={1000}
        required
        readOnly={!form.editable}
        error={form.reasonInvalid || failure?.fields.reason ? text.invalid : undefined}
      />
    </>
  );
}
