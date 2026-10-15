import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { LeaveBatchKind } from "../../domain/entities/leave-batch";
import type { LeavePolicyDefinition } from "../../domain/entities/leave-policy-definition";
import type { LeaveBatchScreenProps } from "../contracts/leave-batch-screen-props";
import { useLeaveBatchTypes } from "../controllers/use-leave-batch-types";
import { leaveBatchMessages } from "../i18n/leave-batch-messages";
import { LeavePreparationPickerPage } from "../pages/leave-preparation-picker-page";
export function LeaveBatchTypePicker(
  props: LeaveBatchScreenProps & {
    kind: LeaveBatchKind;
    onSelect: (type: LeavePolicyDefinition) => void;
    onClose: () => void;
  },
) {
  const state = useLeaveBatchTypes(
      props.accountId,
      props.access,
      props.kind,
      props.leave.loadBatchTypes,
    ),
    text = leaveBatchMessages(props.locale);
  useWorkspaceRevalidation(state.failure);
  return (
    <LeavePreparationPickerPage
      title={text.chooseType}
      locale={props.locale}
      loading={state.loading}
      failure={state.failure}
      ready={state.page !== null}
      firstPage={state.firstPage}
      hasNext={state.page?.nextCursor != null}
      columns={[
        { id: "type", label: text.type },
        { id: "code", label: text.code },
      ]}
      rows={
        state.page?.items.map((type) => ({
          id: type.id,
          actionLabel: type.name,
          cells: [type.name, type.code],
        })) ?? []
      }
      onFirst={state.first}
      onNext={state.next}
      onRefresh={state.refresh}
      onClose={props.onClose}
      onSelect={(id) => {
        const type = state.page?.items.find((item) => item.id === id);
        if (type && !state.loading) props.onSelect(type);
      }}
    />
  );
}
