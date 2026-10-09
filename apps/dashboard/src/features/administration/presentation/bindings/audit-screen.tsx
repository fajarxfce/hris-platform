import { useEffect, useMemo, useSyncExternalStore } from "react";
import { useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AuditSearch } from "../../domain/entities/audit-search";
import type { AdministrationUseCases } from "../contracts/administration-use-cases";
import { AuditController } from "../controllers/audit-controller";
import { useAuditFilters } from "../controllers/use-audit-filters";
import { auditSearchFromParameters, auditSearchParameters } from "../models/audit-route";
import { auditEventView, auditPageView } from "../models/audit-view";
import { AuditPage } from "../pages/audit-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  searchAudit: AdministrationUseCases["searchAudit"];
  companyName: string;
  locale: Locale;
};

export function AuditScreen(props: Props) {
  const [parameters, setParameters] = useSearchParams();
  const query = useMemo(
    () => auditSearchFromParameters(parameters, props.access.companyId),
    [parameters, props.access.companyId],
  );
  return (
    <AuditBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      query={query}
      onQueryChanged={(query) =>
        setParameters(auditSearchParameters(query, props.access.companyId))
      }
    />
  );
}

function AuditBinding({
  access,
  searchAudit,
  query,
  companyName,
  locale,
  onQueryChanged,
}: Props & {
  query: AuditSearch;
  onQueryChanged: (query: AuditSearch) => void;
}) {
  const controller = useMemo(
    () => new AuditController(searchAudit, access, query),
    [searchAudit, access, query],
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
  const filters = useAuditFilters(query, locale, onQueryChanged);
  const view = useMemo(
    () => (state.page ? auditPageView(state.page, locale) : null),
    [state.page, locale],
  );
  const event = useMemo(
    () => (state.selected ? auditEventView(state.selected, locale) : null),
    [state.selected, locale],
  );
  return (
    <AuditPage
      state={state}
      view={view}
      event={event}
      filters={filters}
      companyName={companyName}
      locale={locale}
      onRefresh={controller.refresh}
      onOpen={controller.openEvent}
      onClose={controller.closeEvent}
      hasCursor={query.cursor !== null}
      onReset={filters.reset}
      onFirst={() => onQueryChanged({ ...query, cursor: null })}
      onOlder={() => {
        if (state.page?.nextCursor)
          onQueryChanged({
            ...query,
            from: state.page.from,
            until: state.page.until,
            cursor: state.page.nextCursor,
          });
      }}
    />
  );
}
