import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useCompanyRolePicker } from "../controllers/use-company-role-picker";
import { companyMembershipMessages } from "../i18n/company-membership-messages";
import type { companyRolePickerRows } from "../models/company-membership-view";

export function CompanyRolePickerPage({
  picker,
  rows,
  locale,
  onSelect,
  onClose,
}: {
  picker: ReturnType<typeof useCompanyRolePicker>;
  rows: ReturnType<typeof companyRolePickerRows>;
  locale: Locale;
  onSelect: (id: string) => void;
  onClose: () => void;
}) {
  const text = companyMembershipMessages(locale);
  return (
    <AppDetailsPanel
      open
      title={text.choose}
      closeLabel={messages(locale).close}
      onClose={onClose}
      width={600}
    >
      <div className="app-form" aria-busy={picker.loading}>
        <div className="app-form-actions">
          <AppButton
            appearance="subtle"
            icon={<ArrowClockwise20Regular />}
            aria-label={messages(locale).refresh}
            disabled={picker.loading}
            onClick={picker.refresh}
          />
        </div>
        <AppFailure failure={picker.failure} locale={locale} />
        {picker.loading && <AppLoading label={messages(locale).loading} />}
        {!picker.loading &&
          !picker.failure &&
          (rows.length > 0 ? (
            <AppResourceTable
              title={text.roles}
              columns={[
                { id: "name", label: text.name },
                { id: "status", label: text.status },
                { id: "version", label: text.version },
                { id: "permissions", label: text.permissions },
              ]}
              rows={rows}
              action={{ label: text.select, onOpen: onSelect }}
              minWidth={560}
            />
          ) : (
            <p role="status">{text.noOptions}</p>
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
