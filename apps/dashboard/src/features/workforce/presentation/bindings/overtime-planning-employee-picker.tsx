import { useMemo } from "react";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { useOvertimePlanningEmployees } from "../controllers/use-overtime-planning-employees";
import { overtimePlanningEmployeeRows } from "../models/overtime-planning-view";
import { OvertimePlanningEmployeesPage } from "../pages/overtime-planning-employees-page";

export function OvertimePlanningEmployeePicker(
  props: WorkforceScreenProps & {
    workDate: string;
    onSelect: (id: string) => void;
    onClose: () => void;
  },
) {
  const picker = useOvertimePlanningEmployees(
    props.accountId,
    props.access,
    props.workDate,
    props.workforce.loadOvertimePlanningEmployees,
  );
  useWorkspaceRevalidation(picker.failure);
  const rows = useMemo(
    () => overtimePlanningEmployeeRows(picker.options, props.locale),
    [picker.options, props.locale],
  );
  return (
    <OvertimePlanningEmployeesPage
      picker={picker}
      rows={rows}
      workDate={props.workDate}
      locale={props.locale}
      onClose={props.onClose}
      onSelect={(id) => {
        if (picker.options.some((employee) => employee.id === id)) props.onSelect(id);
      }}
    />
  );
}
