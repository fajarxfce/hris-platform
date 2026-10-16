import { useMemo } from "react";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyMembershipCreationState } from "../models/company-membership-creation-state";
import type { CompanyMembershipFormValues } from "../models/company-membership-form-values";
import type { CompanyMembershipCreationController } from "./company-membership-creation-controller";
import { useMembershipAccessForm } from "./use-membership-access-form";

export function useCompanyMembershipCreationForm(
  controller: CompanyMembershipCreationController,
  state: CompanyMembershipCreationState,
  companyId: CompanyId,
  locale: Locale,
) {
  const values = useMemo<CompanyMembershipFormValues>(
    () => ({ active: state.candidate !== null, permissions: [], roleTemplates: [], reason: "" }),
    [state.candidate],
  );
  return useMembershipAccessForm({
    stage: state.stage,
    values,
    assignablePermissions: state.candidate?.assignablePermissions ?? [],
    accountActive: state.candidate !== null,
    previousMembershipActive: null,
    companyId,
    locale,
    onSave: controller.save,
    onRefresh: controller.refresh,
  });
}
