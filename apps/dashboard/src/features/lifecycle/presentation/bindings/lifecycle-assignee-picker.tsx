import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleAssignee } from "../../domain/entities/lifecycle-assignee";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { useLifecycleAssigneePicker } from "../controllers/use-lifecycle-assignee-picker";
import { LifecycleAssigneePickerPage } from "../pages/lifecycle-assignee-picker-page";

export function LifecycleAssigneePicker(props: {
  accountId: AccountId;
  access: CompanyAccess;
  load: LifecycleUseCases["loadAssignees"];
  excluded: AccountId | null;
  locale: Locale;
  onSelect: (member: LifecycleAssignee) => void;
  onClose: () => void;
}) {
  const picker = useLifecycleAssigneePicker(
    props.accountId,
    props.access,
    props.load,
    props.excluded,
  );
  useWorkspaceRevalidation(picker.failure);
  return (
    <LifecycleAssigneePickerPage
      picker={picker}
      locale={props.locale}
      onClose={props.onClose}
      onSelect={(id) => {
        const member = picker.options.find((item) => item.id === id);
        if (member) props.onSelect(member);
      }}
    />
  );
}
