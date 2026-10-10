import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useLifecycleTemplatePicker } from "../controllers/use-lifecycle-template-picker";
import { lifecycleCaseCreationMessages } from "../i18n/lifecycle-case-creation-messages";
import { lifecycleCaseMessages } from "../i18n/lifecycle-case-messages";
import { lifecycleMessages } from "../i18n/lifecycle-messages";

export function LifecycleTemplatePickerPage({
  picker,
  locale,
  onSelect,
  onClose,
}: {
  picker: ReturnType<typeof useLifecycleTemplatePicker>;
  locale: Locale;
  onSelect: (id: string) => void;
  onClose: () => void;
}) {
  const text = lifecycleCaseCreationMessages(locale);
  const templates = lifecycleMessages(locale);
  const shared = messages(locale);
  return (
    <AppDetailsPanel
      open
      title={text.choose}
      closeLabel={lifecycleCaseMessages(locale).close}
      onClose={onClose}
    >
      <div className="app-report-content" aria-busy={picker.loading}>
        <AppButton onClick={picker.refresh} disabled={picker.loading}>
          {shared.refresh}
        </AppButton>
        <AppFailure failure={picker.failure} locale={locale} />
        {picker.loading && <AppLoading label={shared.loading} />}
        {!picker.loading &&
          !picker.failure &&
          (picker.options.length > 0 ? (
            <AppResourceTable
              title={templates.title}
              columns={[
                { id: "name", label: templates.name },
                { id: "kind", label: templates.kind },
                { id: "version", label: templates.version },
              ]}
              rows={picker.options.map((template) => ({
                id: template.id,
                actionLabel: `${template.name} (${template.code})`,
                cells: [template.name, templates[template.kind], template.version.toString()],
              }))}
              action={{ label: text.select, onOpen: onSelect }}
            />
          ) : (
            <p role="status">{text.empty}</p>
          ))}
        <div className="app-pagination">
          <AppButton onClick={picker.first} disabled={picker.firstPage || picker.loading}>
            {templates.first}
          </AppButton>
          <AppButton onClick={picker.next} disabled={!picker.nextCursor || picker.loading}>
            {templates.next}
          </AppButton>
        </div>
      </div>
    </AppDetailsPanel>
  );
}
