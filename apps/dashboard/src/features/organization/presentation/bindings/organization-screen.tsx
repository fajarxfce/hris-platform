import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { OrganizationUnitSearchInput } from "../../domain/entities/organization-unit-search";
import type { OrganizationUseCases } from "../contracts/organization-use-cases";
import { OrganizationDirectoryController } from "../controllers/organization-directory-controller";
import { useOrganizationFilters } from "../controllers/use-organization-filters";
import {
  organizationSearchFromParameters,
  organizationSearchParameters,
} from "../models/organization-route";
import { organizationDirectoryView } from "../models/organization-view";
import { OrganizationPage } from "../pages/organization-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  organization: OrganizationUseCases;
  companyName: string;
  locale: Locale;
};

export function OrganizationScreen(props: Props) {
  const [parameters, setParameters] = useSearchParams();
  const navigate = useNavigate();
  const search = useMemo(
    () => organizationSearchFromParameters(parameters, props.access.companyId),
    [parameters, props.access.companyId],
  );
  if (parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        to={`/organization/units?${organizationSearchParameters(search, props.access.companyId)}`}
        replace
      />
    );
  return (
    <OrganizationBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      search={search}
      onSearch={(next) => setParameters(organizationSearchParameters(next, props.access.companyId))}
      onOpen={(id) =>
        navigate(
          `/organization/units/${encodeURIComponent(id)}?${organizationSearchParameters(search, props.access.companyId)}`,
        )
      }
    />
  );
}

function OrganizationBinding({
  access,
  organization,
  companyName,
  locale,
  search,
  onSearch,
  onOpen,
}: Props & {
  search: OrganizationUnitSearchInput;
  onSearch: (search: OrganizationUnitSearchInput) => void;
  onOpen: (id: string) => void;
}) {
  const controller = useMemo(
    () => new OrganizationDirectoryController(organization.loadUnits, access, search),
    [organization.loadUnits, access, search],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => {
    controller.activate();
    return controller.deactivate;
  }, [controller]);
  const filters = useOrganizationFilters(search, locale, onSearch);
  const rows = useMemo(
    () => (state.page ? organizationDirectoryView(state.page, locale) : []),
    [state.page, locale],
  );
  return (
    <OrganizationPage
      state={state}
      rows={rows}
      filters={filters}
      companyName={companyName}
      locale={locale}
      firstPage={search.after === null}
      onRefresh={controller.refresh}
      onFirst={() => onSearch({ ...search, after: null })}
      onNext={() => {
        if (state.page?.nextCursor) onSearch({ ...search, after: state.page.nextCursor });
      }}
      onOpen={onOpen}
    />
  );
}
