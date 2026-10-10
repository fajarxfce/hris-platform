import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LoadOrganizationUnits } from "../../../organization/domain/usecases/load-organization-units";
import type { LoadEmployees } from "../../domain/usecases/load-employees";
import { useEmployeeAssignmentPicker } from "../controllers/use-employee-assignment-picker";
import type {
  EmployeeAssignmentKind,
  EmployeeAssignmentOption,
} from "../models/employee-assignment";
import { EmployeeAssignmentPickerPage } from "../pages/employee-assignment-picker-page";

export function EmployeeAssignmentPicker(props: {
  accountId: AccountId;
  access: CompanyAccess;
  kind: EmployeeAssignmentKind;
  effectiveDate: string;
  excludedEmployeeId?: string;
  loadUnits: Pick<LoadOrganizationUnits, "execute">;
  loadEmployees: Pick<LoadEmployees, "execute">;
  locale: Locale;
  onSelect: (option: EmployeeAssignmentOption) => void;
  onClose: () => void;
}) {
  const picker = useEmployeeAssignmentPicker(
    props.accountId,
    props.access,
    props.kind,
    props.effectiveDate,
    props.loadUnits,
    props.loadEmployees,
    props.excludedEmployeeId,
  );
  useWorkspaceRevalidation(picker.failure);
  return (
    <EmployeeAssignmentPickerPage
      kind={props.kind}
      picker={picker}
      locale={props.locale}
      onClose={props.onClose}
      onSelect={(id) => {
        const option = picker.options.find((value) => value.id === id);
        if (option) props.onSelect(option);
      }}
    />
  );
}
