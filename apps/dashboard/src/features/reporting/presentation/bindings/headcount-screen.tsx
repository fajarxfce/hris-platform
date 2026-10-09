import { useEffect, useMemo, useSyncExternalStore } from "react";
import { useSearchParams } from "react-router-dom";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess, CompanyMembership } from "../../../identity/domain/entities/session";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import { HeadcountController } from "../controllers/headcount-controller";
import { useHeadcountFilters } from "../controllers/use-headcount-filters";
import { reportingMessages } from "../i18n/reporting-messages";
import { headcountView } from "../models/headcount-view";
import { HeadcountPage } from "../pages/headcount-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  loadHeadcount: ReportingUseCases["loadHeadcount"];
  companies: readonly CompanyMembership[];
  timezone: string;
  locale: Locale;
};

export function HeadcountScreen(props: Props) {
  const [parameters, setParameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const asOf = parameters.get("asOf") ?? today;
  const selected = useMemo(
    () =>
      Object.freeze(
        (parameters.get("companies") ?? props.access.companyId)
          .split(",", 34)
          .map((id) => id.toLowerCase() as CompanyId),
      ),
    [parameters, props.access.companyId],
  );
  return (
    <HeadcountBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      asOf={asOf}
      selected={selected}
      onFiltersChanged={(date, companies) =>
        setParameters({ asOf: date, companies: [...companies].sort().join(",") })
      }
    />
  );
}

function HeadcountBinding({
  access,
  loadHeadcount,
  asOf,
  companies,
  selected,
  locale,
  onFiltersChanged,
}: Props & {
  asOf: string;
  selected: readonly CompanyId[];
  onFiltersChanged: (date: string, companies: readonly CompanyId[]) => void;
}) {
  const controller = useMemo(
    () => new HeadcountController(loadHeadcount, access, asOf, selected),
    [loadHeadcount, access, asOf, selected],
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
    () => (state.report ? headcountView(state.report, companies, locale) : null),
    [state.report, companies, locale],
  );
  const filters = useHeadcountFilters(asOf, selected, companies, locale, onFiltersChanged);
  return (
    <HeadcountPage
      state={state}
      view={view}
      filters={filters}
      companyName={
        selected.length === 1
          ? (companies.find((company) => company.id === selected[0])?.name ?? "")
          : reportingMessages(locale).companyGroup
      }
      locale={locale}
      onRefresh={controller.refresh}
    />
  );
}
