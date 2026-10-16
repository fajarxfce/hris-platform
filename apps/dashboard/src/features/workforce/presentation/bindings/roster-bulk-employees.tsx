import { useMemo } from "react";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { RosterBulkEmployeePicker } from "../components/roster-bulk-employee-picker";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { useRosterBulkEmployees } from "../controllers/use-roster-bulk-employees";
import type { useRosterBulkForm } from "../controllers/use-roster-bulk-form";
import { calendarMessages } from "../i18n/calendar-messages";

export function RosterBulkEmployees(
  props: WorkforceScreenProps & { form: ReturnType<typeof useRosterBulkForm>; asOf: string },
) {
  const state = useRosterBulkEmployees(
    props.accountId,
    props.access,
    props.asOf,
    props.workforce.loadWorkforceEmployees,
  );
  const rows = useMemo(() => {
    const selected = new Set(props.form.employees.map((employee) => employee.id));
    const text = calendarMessages(props.locale);
    return (state.page?.items ?? []).map((employee) => ({
      id: employee.id,
      label: `${employee.legalName} (${employee.employeeNumber})`,
      cells: [employee.employeeNumber, employee.legalName, text[employee.status]],
      selected: selected.has(employee.id),
    }));
  }, [state.page, props.form.employees, props.locale]);
  const selectedCount = rows.filter((row) => row.selected).length;
  useWorkspaceRevalidation(state.failure);
  return (
    <RosterBulkEmployeePicker
      state={state}
      locale={props.locale}
      rows={rows}
      pageChecked={selectedCount === 0 ? false : selectedCount === rows.length ? true : "mixed"}
      onToggle={(id, selected) => {
        const employee = state.page?.items.find((value) => value.id === id);
        if (employee) props.form.toggleEmployee(employee, selected);
      }}
      onTogglePage={(selected) => {
        if (state.page) props.form.togglePage(state.page.items, selected);
      }}
    />
  );
}
