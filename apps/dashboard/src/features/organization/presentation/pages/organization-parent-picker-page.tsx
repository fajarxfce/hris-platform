import { Text } from "@fluentui/react-components";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { OrganizationUnitKind } from "../../domain/entities/organization-unit";
import type { useOrganizationFilters } from "../controllers/use-organization-filters";
import { organizationMessages } from "../i18n/organization-messages";
import type { OrganizationDirectoryState } from "../models/organization-directory-state";
import type { organizationDirectoryView } from "../models/organization-view";

export function OrganizationParentPickerPage({
  state,
  filters,
  rows,
  kinds,
  locale,
  firstPage,
  onFirst,
  onNext,
  onSelect,
  onClose,
}: {
  state: OrganizationDirectoryState;
  filters: ReturnType<typeof useOrganizationFilters>;
  rows: ReturnType<typeof organizationDirectoryView>;
  kinds: readonly OrganizationUnitKind[];
  locale: Locale;
  firstPage: boolean;
  onFirst: () => void;
  onNext: () => void;
  onSelect: (id: string) => void;
  onClose: () => void;
}) {
  const text = organizationMessages(locale);
  return (
    <AppDetailsPanel
      open
      title={text.chooseParent}
      closeLabel={messages(locale).close}
      onClose={onClose}
    >
      <div className="app-form">
        <form className="app-form" onSubmit={filters.apply} noValidate>
          <AppTextField label={text.query} maxLength={120} {...filters.query} />
          <AppSelect label={text.kind} {...filters.kind}>
            {kinds.map((kind) => (
              <option key={kind} value={kind}>
                {text[kind]}
              </option>
            ))}
          </AppSelect>
          <AppSelect label={text.status} {...filters.active}>
            <option value="">{text.allStatuses}</option>
            <option value="true">{text.active}</option>
            <option value="false">{text.inactive}</option>
          </AppSelect>
          <AppButton type="submit" appearance="primary">
            {text.apply}
          </AppButton>
          {filters.error && <p role="alert">{filters.error}</p>}
        </form>
        <AppFailure failure={state.failure} locale={locale} />
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.page &&
          (rows.length > 0 ? (
            <AppResourceTable
              title={text.parentResults}
              rows={rows}
              columns={[
                { id: "code", label: text.code },
                { id: "name", label: text.name },
                { id: "kind", label: text.kind },
                { id: "status", label: text.status },
              ]}
              action={{ label: text.select, onOpen: onSelect }}
            />
          ) : (
            <Text role="status">{text.empty}</Text>
          ))}
        <div className="app-pagination">
          <AppButton disabled={firstPage || state.stage === "loading"} onClick={onFirst}>
            {text.first}
          </AppButton>
          <AppButton
            disabled={!state.page?.nextCursor || state.stage === "loading"}
            onClick={onNext}
          >
            {text.next}
          </AppButton>
        </div>
      </div>
    </AppDetailsPanel>
  );
}
