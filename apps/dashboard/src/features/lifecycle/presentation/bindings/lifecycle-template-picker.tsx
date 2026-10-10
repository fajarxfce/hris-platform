import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleTemplate } from "../../domain/entities/lifecycle-template";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { useLifecycleTemplatePicker } from "../controllers/use-lifecycle-template-picker";
import { LifecycleTemplatePickerPage } from "../pages/lifecycle-template-picker-page";

export function LifecycleTemplatePicker(props: {
  accountId: AccountId;
  access: CompanyAccess;
  load: LifecycleUseCases["loadTemplates"];
  locale: Locale;
  onSelect: (template: LifecycleTemplate) => void;
  onClose: () => void;
}) {
  const picker = useLifecycleTemplatePicker(props.accountId, props.access, props.load);
  useWorkspaceRevalidation(picker.failure);
  return (
    <LifecycleTemplatePickerPage
      picker={picker}
      locale={props.locale}
      onClose={props.onClose}
      onSelect={(id) => {
        const template = picker.options.find((item) => item.id === id);
        if (template) props.onSelect(template);
      }}
    />
  );
}
