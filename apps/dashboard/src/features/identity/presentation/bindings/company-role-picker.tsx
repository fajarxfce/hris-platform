import { useMemo } from "react";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { RoleTemplate } from "../../domain/entities/role-template";
import type { CompanyAccess } from "../../domain/entities/session";
import type { LoadRoleTemplates } from "../../domain/usecases/load-role-templates";
import { useCompanyRolePicker } from "../controllers/use-company-role-picker";
import { companyRolePickerRows } from "../models/company-membership-view";
import { CompanyRolePickerPage } from "../pages/company-role-picker-page";

export function CompanyRolePicker(props: {
  accountId: AccountId;
  access: CompanyAccess;
  load: Pick<LoadRoleTemplates, "execute">;
  excludedRoleIds: readonly string[];
  locale: Locale;
  onSelect: (role: RoleTemplate) => void;
  onClose: () => void;
}) {
  const picker = useCompanyRolePicker(props.accountId, props.access, props.load);
  useWorkspaceRevalidation(picker.failure);
  const rows = useMemo(
    () => companyRolePickerRows(picker.items, props.excludedRoleIds, props.locale),
    [picker.items, props.excludedRoleIds, props.locale],
  );
  return (
    <CompanyRolePickerPage
      picker={picker}
      rows={rows}
      locale={props.locale}
      onClose={props.onClose}
      onSelect={(id) => {
        const role = picker.items.find((value) => value.id === id);
        if (role?.active && !props.excludedRoleIds.includes(id)) props.onSelect(role);
      }}
    />
  );
}
