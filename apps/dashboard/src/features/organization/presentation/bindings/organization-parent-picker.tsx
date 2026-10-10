import { useEffect, useMemo, useReducer, useSyncExternalStore } from "react";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type {
  OrganizationUnit,
  OrganizationUnitKind,
} from "../../domain/entities/organization-unit";
import type { OrganizationUnitSearchInput } from "../../domain/entities/organization-unit-search";
import type { OrganizationUseCases } from "../contracts/organization-use-cases";
import { OrganizationDirectoryController } from "../controllers/organization-directory-controller";
import { useOrganizationFilters } from "../controllers/use-organization-filters";
import { organizationDirectoryView } from "../models/organization-view";
import { OrganizationParentPickerPage } from "../pages/organization-parent-picker-page";

export function OrganizationParentPicker({
  access,
  loadUnits,
  kinds,
  excludedId,
  onSelect,
  onClose,
  locale,
}: {
  access: CompanyAccess;
  loadUnits: OrganizationUseCases["loadUnits"];
  kinds: readonly OrganizationUnitKind[];
  excludedId: string;
  onSelect: (unit: OrganizationUnit) => void;
  onClose: () => void;
  locale: Locale;
}) {
  const [search, apply] = useReducer(
    (_previous: OrganizationUnitSearchInput, next: OrganizationUnitSearchInput) => next,
    { query: "", kind: kinds[0] ?? null, active: "true", after: null },
  );
  const controller = useMemo(
    () => new OrganizationDirectoryController(loadUnits, access, search),
    [loadUnits, access, search],
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
  const filters = useOrganizationFilters(search, locale, apply);
  const eligible = useMemo(
    () =>
      state.page?.items.filter((unit) => unit.id !== excludedId && kinds.includes(unit.kind)) ?? [],
    [state.page, excludedId, kinds],
  );
  const rows = useMemo(
    () => organizationDirectoryView({ items: eligible, nextCursor: null }, locale),
    [eligible, locale],
  );
  return (
    <OrganizationParentPickerPage
      state={state}
      filters={filters}
      rows={rows}
      kinds={kinds}
      locale={locale}
      firstPage={search.after === null}
      onClose={onClose}
      onFirst={() => apply({ ...search, after: null })}
      onNext={() => {
        if (state.page?.nextCursor) apply({ ...search, after: state.page.nextCursor });
      }}
      onSelect={(id) => {
        const selected = eligible.find((unit) => unit.id === id);
        if (selected) onSelect(selected);
      }}
    />
  );
}
