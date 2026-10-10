import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { organizationUnitKinds } from "../../domain/entities/organization-unit";
import type { useOrganizationFilters } from "../controllers/use-organization-filters";
import { organizationMessages } from "../i18n/organization-messages";
import type { OrganizationDirectoryState } from "../models/organization-directory-state";
import type { organizationDirectoryView } from "../models/organization-view";

export function OrganizationPage({
  state,
  rows,
  filters,
  companyName,
  locale,
  firstPage,
  onRefresh,
  onFirst,
  onNext,
  onOpen,
}: {
  state: OrganizationDirectoryState;
  rows: ReturnType<typeof organizationDirectoryView>;
  filters: ReturnType<typeof useOrganizationFilters>;
  companyName: string;
  locale: Locale;
  firstPage: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const text = organizationMessages(locale);
  const shared = messages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={companyName}
        actions={
          <AppButton
            icon={<ArrowClockwise20Regular />}
            disabled={state.stage === "loading"}
            onClick={onRefresh}
          >
            {shared.refresh}
          </AppButton>
        }
      />
      <form onSubmit={filters.apply} noValidate>
        <AppCommandBar label={text.filters}>
          <div className="app-organization-filters">
            <AppTextField label={text.query} maxLength={120} {...filters.query} />
            <AppSelect label={text.kind} {...filters.kind}>
              <option value="">{text.allKinds}</option>
              {organizationUnitKinds.map((kind) => (
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
          </div>
          {filters.error && <p role="alert">{filters.error}</p>}
        </AppCommandBar>
      </form>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={shared.loading} />}
      <div className="app-report-content">
        {state.page &&
          (rows.length === 0 ? (
            <Text role="status">{text.empty}</Text>
          ) : (
            <AppResourceTable
              title={text.directory}
              columns={[
                { id: "code", label: text.code },
                { id: "name", label: text.name },
                { id: "kind", label: text.kind },
                { id: "status", label: text.status },
              ]}
              rows={rows}
              action={{ label: text.view, onOpen }}
            />
          ))}
        <fieldset className="app-pagination" aria-label={text.page}>
          <AppButton onClick={onFirst} disabled={firstPage || state.stage === "loading"}>
            {text.first}
          </AppButton>
          <AppButton
            onClick={onNext}
            disabled={state.page?.nextCursor == null || state.stage === "loading"}
          >
            {text.next}
          </AppButton>
        </fieldset>
      </div>
    </section>
  );
}
