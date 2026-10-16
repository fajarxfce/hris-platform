import { useMemo } from "react";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyMembershipEditorState } from "../models/company-membership-editor-state";
import { companyMembershipFormValues } from "../models/company-membership-form-values";
import type { CompanyMembershipEditorController } from "./company-membership-editor-controller";
import { useMembershipAccessForm } from "./use-membership-access-form";

export function useCompanyMembershipForm(
  controller: CompanyMembershipEditorController,
  state: CompanyMembershipEditorState,
  companyId: CompanyId,
  locale: Locale,
) {
  const values = useMemo(() => companyMembershipFormValues(state.grant), [state.grant]);
  return useMembershipAccessForm({
    stage: state.stage,
    values,
    assignablePermissions: state.permissions,
    accountActive: state.grant?.member.accountActive ?? false,
    previousMembershipActive: state.grant?.member.membershipActive ?? null,
    companyId,
    locale,
    onSave: controller.save,
    onRefresh: controller.refresh,
  });
}
