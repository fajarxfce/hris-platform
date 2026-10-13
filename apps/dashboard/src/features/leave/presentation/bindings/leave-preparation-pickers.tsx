import { useEffect } from "react";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { LeaveEvidenceOption } from "../../domain/entities/leave-evidence-option";
import type { LeavePreparationEmployee } from "../../domain/entities/leave-preparation-employee";
import type { LeaveRequestType } from "../../domain/entities/leave-request-type";
import type { LeaveCreationProps } from "../contracts/leave-creation-props";
import type { LeaveCreationController } from "../controllers/leave-creation-controller";
import { useLeavePreparationEmployees } from "../controllers/use-leave-preparation-employees";
import { useLeavePreparationEvidence } from "../controllers/use-leave-preparation-evidence";
import { useLeaveRequestTypes } from "../controllers/use-leave-request-types";
import { leaveCreationMessages } from "../i18n/leave-creation-messages";
import { LeavePreparationPickerPage } from "../pages/leave-preparation-picker-page";

type Props = LeaveCreationProps & { controller: LeaveCreationController; onClose: () => void };

export function LeaveEmployeePicker(
  props: Props & { onSelect: (employee: LeavePreparationEmployee) => void },
) {
  const state = useLeavePreparationEmployees(
      props.accountId,
      props.access,
      props.leave.loadPreparationEmployees,
    ),
    text = leaveCreationMessages(props.locale);
  useWorkspaceRevalidation(state.failure);
  useEffect(() => {
    if (state.failure) props.controller.withdrawAccess(state.failure);
  }, [props.controller, state.failure]);
  return (
    <LeavePreparationPickerPage
      title={text.chooseEmployee}
      locale={props.locale}
      loading={state.loading}
      failure={state.failure}
      ready={state.directory !== null}
      firstPage={state.firstPage}
      hasNext={state.directory?.employees.nextCursor != null}
      search={state}
      rows={
        state.directory?.employees.items.map((employee) => ({
          id: employee.id,
          actionLabel: employee.legalName,
          cells: [employee.legalName, employee.employeeNumber],
        })) ?? []
      }
      columns={[
        { id: "name", label: text.employee },
        { id: "number", label: text.number },
      ]}
      onRefresh={state.refresh}
      onFirst={state.first}
      onNext={state.next}
      onClose={props.onClose}
      onSelect={(id) => {
        const employee = state.directory?.employees.items.find((item) => item.id === id);
        if (employee) props.onSelect(employee);
      }}
    />
  );
}

export function LeaveTypePicker(
  props: Props & { asOf: string; onSelect: (type: LeaveRequestType) => void },
) {
  const state = useLeaveRequestTypes(
      props.accountId,
      props.access,
      props.asOf,
      props.leave.loadRequestTypes,
    ),
    text = leaveCreationMessages(props.locale);
  useWorkspaceRevalidation(state.failure);
  useEffect(() => {
    if (state.failure) props.controller.withdrawAccess(state.failure);
  }, [props.controller, state.failure]);
  return (
    <LeavePreparationPickerPage
      title={text.chooseType}
      locale={props.locale}
      loading={state.loading}
      failure={state.failure}
      ready={state.page !== null}
      firstPage={state.firstPage}
      hasNext={state.page?.nextCursor != null}
      rows={
        state.page?.items.map((type) => ({
          id: type.id,
          actionLabel: type.name,
          cells: [type.name, type.code, type.attachmentRequired ? text.required : "—"],
        })) ?? []
      }
      columns={[
        { id: "name", label: text.type },
        { id: "code", label: text.code },
        { id: "document", label: text.documents },
      ]}
      onRefresh={state.refresh}
      onFirst={state.first}
      onNext={state.next}
      onClose={props.onClose}
      onSelect={(id) => {
        const type = state.page?.items.find((item) => item.id === id);
        if (type) props.onSelect(type);
      }}
    />
  );
}

export function LeaveEvidencePicker(
  props: Props & {
    employeeId: string;
    selected: readonly LeaveEvidenceOption[];
    onSelect: (evidence: LeaveEvidenceOption) => void;
  },
) {
  const state = useLeavePreparationEvidence(
      props.accountId,
      props.access,
      props.employeeId,
      props.leave.loadPreparationEvidence,
    ),
    text = leaveCreationMessages(props.locale);
  useWorkspaceRevalidation(state.failure);
  useEffect(() => {
    if (state.failure) props.controller.withdrawAccess(state.failure);
  }, [props.controller, state.failure]);
  return (
    <LeavePreparationPickerPage
      title={text.chooseDocuments}
      locale={props.locale}
      loading={state.loading}
      failure={state.failure}
      ready={state.page !== null}
      firstPage={state.firstPage}
      hasNext={state.page?.nextCursor != null}
      rows={
        state.page?.items.map((item) => ({
          id: item.revisionId,
          actionLabel: item.title,
          actionDisabled:
            props.selected.length >= 3 ||
            props.selected.some((value) => value.revisionId === item.revisionId),
          cells: [
            item.title,
            item.fileName,
            props.selected.some((value) => value.revisionId === item.revisionId)
              ? text.selected
              : "—",
          ],
        })) ?? []
      }
      columns={[
        { id: "title", label: text.titleColumn },
        { id: "file", label: text.file },
        { id: "selected", label: text.selected },
      ]}
      onRefresh={state.refresh}
      onFirst={state.first}
      onNext={state.next}
      onClose={props.onClose}
      onSelect={(id) => {
        const item = state.page?.items.find((item) => item.revisionId === id);
        if (item) props.onSelect(item);
      }}
    />
  );
}
