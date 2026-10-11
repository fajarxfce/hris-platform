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
import type { EmploymentTransferState } from "../models/employment-transfer-state";
import type { EmploymentTransferController } from "./employment-transfer-controller";

type Fields = {
  employeeNumber: string;
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
export function useEmploymentTransferForm(
  controller: EmploymentTransferController,
  state: EmploymentTransferState,
) {
  const values = useMemo<Fields>(
    () => ({
      employeeNumber: "",
      endDate: state.review?.employee.terms.endDate ?? "",
      contract: state.review?.employee.terms.contract ?? "PERMANENT",
      status: state.review?.employee.terms.status ?? "ACTIVE",
      reason: "",
      branch: null,
      department: null,
      position: null,
      costCenter: null,
      manager: null,
    }),
    [state.review],
  );
  const form = useForm<Fields>({ values });
  const employeeNumber = useController({ name: "employeeNumber", control: form.control });
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
        : state.stage === "confirming" || (state.stage !== "saved" && form.formState.isDirty)
          ? "dirty"
          : "none",
  );
  return {
    employeeNumber: employeeNumber.field,
    endDate: endDate.field,
    contract: contract.field,
    status: status.field,
    reason: reason.field,
    editable,
    picker,
    canSelectTarget: ["choosing", "loading", "editing", "conflict", "unavailable"].includes(
      state.stage,
    ),
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
    selectTarget: (id: string) =>
      depart(() => {
        openPicker(null);
        form.reset(values);
        void controller.selectTarget(id);
      }),
    refresh: () =>
      depart(() => {
        openPicker(null);
        form.reset(values);
        void controller.refresh();
      }),
    submit: form.handleSubmit((fields) =>
      controller.requestTransfer({
        employeeNumber: fields.employeeNumber,
        reason: fields.reason,
        terms: {
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
