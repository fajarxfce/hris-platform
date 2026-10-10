import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useLifecycleAssigneePicker } from "../controllers/use-lifecycle-assignee-picker";
import { lifecycleCaseMessages } from "../i18n/lifecycle-case-messages";

export function LifecycleAssigneePickerPage({
  picker,
  locale,
  onSelect,
  onClose,
}: {
  picker: ReturnType<typeof useLifecycleAssigneePicker>;
  locale: Locale;
  onSelect: (id: string) => void;
  onClose: () => void;
}) {
  const text = lifecycleCaseMessages(locale);
  return (
    <AppDetailsPanel open title={text.chooseMember} closeLabel={text.close} onClose={onClose}>
      <div className="app-report-content" aria-busy={picker.loading}>
        <form className="app-form" onSubmit={picker.apply} noValidate>
          <AppTextField label={text.searchMember} {...picker.query} maxLength={120} />
          <AppButton type="submit" appearance="primary">
            {text.search}
          </AppButton>
        </form>
        <AppFailure failure={picker.failure} locale={locale} />
        {picker.failure && <AppButton onClick={picker.refresh}>{messages(locale).retry}</AppButton>}
        {picker.loading && <AppLoading label={messages(locale).loading} />}
        {!picker.loading &&
          !picker.failure &&
          (picker.options.length > 0 ? (
            <AppResourceTable
              title={text.members}
              columns={[
                { id: "name", label: text.name },
                { id: "id", label: text.accountId },
              ]}
              rows={picker.options.map((member) => ({
                id: member.id,
                actionLabel: `${member.displayName} · ${member.id}`,
                cells: [member.displayName, member.id],
              }))}
              action={{ label: text.select, onOpen: onSelect }}
            />
          ) : (
            <p role="status">{text.membersEmpty}</p>
          ))}
        <div className="app-pagination">
          <AppButton onClick={picker.first} disabled={picker.firstPage || picker.loading}>
            {text.first}
          </AppButton>
          <AppButton onClick={picker.next} disabled={!picker.nextCursor || picker.loading}>
            {text.next}
          </AppButton>
        </div>
      </div>
    </AppDetailsPanel>
  );
}
