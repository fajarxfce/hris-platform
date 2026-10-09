import { useEffect, useMemo, useSyncExternalStore } from "react";
import { useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import { HeadcountController } from "../controllers/headcount-controller";
import { useHeadcountFilters } from "../controllers/use-headcount-filters";
import { headcountView } from "../models/headcount-view";
import { HeadcountPage } from "../pages/headcount-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  loadHeadcount: ReportingUseCases["loadHeadcount"];
  companyName: string;
  timezone: string;
  locale: Locale;
};

export function HeadcountScreen(props: Props) {
  const [parameters, setParameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const asOf = parameters.get("asOf") ?? today;
  return (
    <HeadcountBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      asOf={asOf}
      onDateChanged={(date) => setParameters({ asOf: date })}
    />
  );
}

function HeadcountBinding({
  access,
  loadHeadcount,
  asOf,
  companyName,
  locale,
  onDateChanged,
}: Props & {
  asOf: string;
  onDateChanged: (date: string) => void;
}) {
  const controller = useMemo(
    () => new HeadcountController(loadHeadcount, access, asOf),
    [loadHeadcount, access, asOf],
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
    () => (state.report ? headcountView(state.report, locale) : null),
    [state.report, locale],
  );
  const filters = useHeadcountFilters(asOf, locale, onDateChanged);
  return (
    <HeadcountPage
      state={state}
      view={view}
      filters={filters}
      companyName={companyName}
      locale={locale}
      onRefresh={controller.refresh}
    />
  );
}
