import { useReducer } from "react";
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
import type { EmployeeCreationState } from "../models/employee-creation-state";
import type { EmployeeCreationController } from "./employee-creation-controller";

type Fields = {
  employeeNumber: string;
  legalName: string;
  birthDate: string;
  nationality: string;
  email: string;
  startDate: string;
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

export function useEmployeeCreationForm(
  controller: EmployeeCreationController,
  state: EmployeeCreationState,
  today: string,
) {
  const form = useForm<Fields>({
    defaultValues: {
      employeeNumber: "",
      legalName: "",
      birthDate: "",
      nationality: "",
      email: "",
      startDate: today,
      endDate: "",
      contract: "PERMANENT",
      status: "ACTIVE",
      reason: "",
      branch: null,
      department: null,
      position: null,
      costCenter: null,
      manager: null,
    },
  });
  const employeeNumber = useController({ name: "employeeNumber", control: form.control });
  const legalName = useController({ name: "legalName", control: form.control });
  const birthDate = useController({ name: "birthDate", control: form.control });
  const nationality = useController({ name: "nationality", control: form.control });
  const email = useController({ name: "email", control: form.control });
  const startDate = useController({ name: "startDate", control: form.control });
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
  useNavigationProtection(
    state.stage === "saving"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" && form.formState.isDirty
          ? "dirty"
          : "none",
  );
  return {
    employeeNumber: employeeNumber.field,
    legalName: legalName.field,
    birthDate: birthDate.field,
    nationality: nationality.field,
    email: email.field,
    startDate: startDate.field,
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
    submit: form.handleSubmit((fields) =>
      controller.save({
        employeeNumber: fields.employeeNumber,
        legalName: fields.legalName,
        birthDate: fields.birthDate || null,
        nationality: fields.nationality,
        email: fields.email || null,
        startDate: fields.startDate,
        endDate: fields.endDate || null,
        contract: fields.contract,
        status: fields.status,
        reason: fields.reason,
        branchId: (fields.branch?.id as OrganizationUnitId) ?? null,
        departmentId: (fields.department?.id as OrganizationUnitId) ?? null,
        positionId: (fields.position?.id as OrganizationUnitId) ?? null,
        costCenterId: (fields.costCenter?.id as OrganizationUnitId) ?? null,
        managerId: (fields.manager?.id as EmployeeId) ?? null,
      }),
    ),
  };
}
