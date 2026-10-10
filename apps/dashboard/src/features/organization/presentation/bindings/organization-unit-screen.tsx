import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { OrganizationUseCases } from "../contracts/organization-use-cases";
import { OrganizationUnitController } from "../controllers/organization-unit-controller";
import {
  organizationSearchFromParameters,
  organizationSearchParameters,
} from "../models/organization-route";
import { organizationUnitView } from "../models/organization-view";
import { OrganizationUnitPage } from "../pages/organization-unit-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  organization: OrganizationUseCases;
  companyName: string;
  locale: Locale;
};

export function OrganizationUnitScreen(props: Props) {
  const { unitId = "" } = useParams();
  const [parameters] = useSearchParams();
  const search = organizationSearchFromParameters(parameters, props.access.companyId);
  const pinned = organizationSearchParameters(search, props.access.companyId).toString();
  const backTo = `/organization/units?${pinned}`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={backTo} replace />;
  if (!parameters.has("company"))
    return <Navigate to={`/organization/units/${encodeURIComponent(unitId)}?${pinned}`} replace />;
  return (
    <OrganizationUnitBinding
      key={`${props.accountId}:${props.access.companyId}:${unitId}`}
      {...props}
      id={unitId}
      backTo={backTo}
      parameters={pinned}
    />
  );
}

function OrganizationUnitBinding({
  access,
  organization,
  companyName,
  locale,
  id,
  backTo,
  parameters,
}: Props & {
  id: string;
  backTo: string;
  parameters: string;
}) {
  const controller = useMemo(
    () => new OrganizationUnitController(organization.loadUnit, access, id),
    [organization.loadUnit, access, id],
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
  const view = useMemo(
    () =>
      state.details
        ? {
            unit: organizationUnitView(state.details.unit, locale),
            parent: state.details.parent
              ? organizationUnitView(state.details.parent, locale)
              : null,
          }
        : null,
    [state.details, locale],
  );
  return (
    <OrganizationUnitPage
      state={state}
      view={view}
      companyName={companyName}
      locale={locale}
      backTo={backTo}
      onRefresh={controller.refresh}
      parentTo={
        state.details?.parent
          ? `/organization/units/${state.details.parent.id}?${parameters}`
          : null
      }
    />
  );
}
