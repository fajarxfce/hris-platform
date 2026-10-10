import { useMemo, useReducer } from "react";
import { useController, useForm, useWatch } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { OrganizationUnitId } from "../../../organization/domain/entities/organization-unit";
import type { EmployeeId } from "../../domain/entities/employee";
import type { EmploymentTerms } from "../../domain/entities/employment-terms";
import {
  assignmentFieldNames,
  type EmployeeAssignmentKind,
  type EmployeeAssignmentOption,
} from "../models/employee-assignment";
import { employmentAssignmentOptions } from "../models/employment-assignment-view";
import type { EmploymentEditorState } from "../models/employment-editor-state";
import type { EmploymentEditorController } from "./employment-editor-controller";

type Fields = {
  effectiveFrom: string;
  endDate: string;
  contract: EmploymentTerms["contract"];
  status: EmploymentTerms["status"];
  reason: string;
  branch: EmployeeAssignmentOption | null;
  department: EmployeeAssignmentOption | null;
  position: EmployeeAssignmentOption | null;
  costCenter: EmployeeAssignmentOption | null;
  manager: EmployeeAssignmentOption | null;
};
export function useEmploymentEditorForm(
  controller: EmploymentEditorController,
  state: EmploymentEditorState,
) {
  const values = useMemo<Fields>(
    () => ({
      effectiveFrom: state.details?.asOf ?? "",
      endDate: state.details?.employee.terms.endDate ?? "",
      contract: state.details?.employee.terms.contract ?? "PERMANENT",
      status: state.details?.employee.terms.status ?? "ACTIVE",
      reason: "",
      ...employmentAssignmentOptions(state.details),
    }),
    [state.details],
  );
  const form = useForm<Fields>({ values });
  const effectiveFrom = useController({ name: "effectiveFrom", control: form.control });
  const endDate = useController({ name: "endDate", control: form.control });
  const contract = useController({ name: "contract", control: form.control });
  const status = useController({ name: "status", control: form.control });
  const reason = useController({ name: "reason", control: form.control });
  const assignments = useWatch({
    control: form.control,
    name: ["branch", "department", "position", "costCenter", "manager"],
  });
  const [picker, openPicker] = useReducer(
    (_previous: EmployeeAssignmentKind | null, next: EmployeeAssignmentKind | null) => next,
    null,
  );
  const editable = state.stage === "editing";
  const depart = useNavigationProtection(
    state.stage === "saving"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" && form.formState.isDirty
          ? "dirty"
          : "none",
  );
  return {
    effectiveFrom: effectiveFrom.field,
    endDate: endDate.field,
    contract: contract.field,
    status: status.field,
    reason: reason.field,
    editable,
    picker,
    assignments: (Object.keys(assignmentFieldNames) as EmployeeAssignmentKind[]).map(
      (kind, index) => ({ kind, value: assignments[index] ?? null }),
    ),
    choose: (kind: EmployeeAssignmentKind) => {
      if (editable) openPicker(kind);
    },
    closePicker: () => openPicker(null),
    select: (option: EmployeeAssignmentOption) => {
      if (editable && picker)
        form.setValue(assignmentFieldNames[picker], option, { shouldDirty: true });
      openPicker(null);
    },
    clear: (kind: EmployeeAssignmentKind) => {
      if (editable) form.setValue(assignmentFieldNames[kind], null, { shouldDirty: true });
    },
    refresh: () =>
      depart(() => {
        void controller.refresh();
      }),
    submit: form.handleSubmit((fields) =>
      controller.save({
        reason: fields.reason,
        terms: {
          effectiveFrom: fields.effectiveFrom,
          endDate: fields.endDate || null,
          contract: fields.contract,
          status: fields.status,
          branchId: (fields.branch?.id as OrganizationUnitId) ?? null,
          departmentId: (fields.department?.id as OrganizationUnitId) ?? null,
          positionId: (fields.position?.id as OrganizationUnitId) ?? null,
          costCenterId: (fields.costCenter?.id as OrganizationUnitId) ?? null,
          managerId: (fields.manager?.id as EmployeeId) ?? null,
        },
      }),
    ),
  };
}
