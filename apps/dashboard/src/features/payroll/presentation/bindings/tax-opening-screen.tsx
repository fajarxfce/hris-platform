import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { TaxOpeningController } from "../controllers/tax-opening-controller";
import { useTaxOpeningYear } from "../controllers/use-tax-opening-year";
import { taxOpeningReviewParameters } from "../models/tax-opening-route";
import { taxOpeningReviewView, taxOpeningView } from "../models/tax-opening-view";
import { TaxOpeningPage } from "../pages/tax-opening-page";
export function TaxOpeningScreen(props: PayrollScreenProps) {
  const [parameters, setParameters] = useSearchParams(),
    { employeeId = "" } = useParams();
  const today = useMemo(
    () => companyDate(props.timezone, new Date()).slice(0, 4),
    [props.timezone],
  );
  const year = parameters.get("year") ?? today,
    after = parameters.get("historyAfter"),
    tab = parameters.get("tab") === "history" ? "history" : "overview";
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/tax-openings?company=${props.access.companyId}`} replace />;
  const canonical = taxOpeningReviewParameters(props.access.companyId, year, after, tab);
  if (!parameters.has("company") || !parameters.has("year"))
    return (
      <Navigate
        to={`/payroll/tax-openings/${encodeURIComponent(employeeId)}?${canonical}`}
        replace
      />
    );
  return (
    <TaxOpeningBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}`}
      {...props}
      employee={employeeId}
      year={year}
      after={after}
      tab={tab}
      onQuery={(year, after, tab) =>
        setParameters(taxOpeningReviewParameters(props.access.companyId, year, after, tab))
      }
    />
  );
}
function TaxOpeningBinding({
  payroll,
  access,
  employee,
  year,
  after,
  tab,
  locale,
  timezone,
  companyName,
  onQuery,
}: PayrollScreenProps & {
  employee: string;
  year: string;
  after: string | null;
  tab: string;
  onQuery: (year: string, after: string | null, tab: string) => void;
}) {
  const controller = useMemo(
    () => new TaxOpeningController(payroll, access, employee, year, after),
    [payroll, access, employee, year, after],
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
  useWorkspaceRevalidation(state.failure);
  const filter = useTaxOpeningYear(year, (value) => onQuery(value, null, "overview"));
  const view = useMemo(
    () => (state.review?.current ? taxOpeningView(state.review.current, locale, timezone) : null),
    [state.review, locale, timezone],
  );
  const review = useMemo(
    () => (state.review ? taxOpeningReviewView(state.review, locale, timezone) : null),
    [state.review, locale, timezone],
  );
  const selected = useMemo(
    () => (state.selected ? taxOpeningView(state.selected, locale, timezone) : null),
    [state.selected, locale, timezone],
  );
  const query = taxOpeningReviewParameters(access.companyId, year);
  return (
    <TaxOpeningPage
      state={state}
      view={view}
      review={review}
      selected={selected}
      filter={filter}
      locale={locale}
      companyName={companyName}
      tab={tab}
      first={after === null}
      backTo={`/payroll/tax-openings?${query}`}
      editTo={
        state.review?.availableActions.includes("PREPARE")
          ? `/payroll/tax-openings/${encodeURIComponent(employee)}/edit?${query}`
          : null
      }
      verifyTo={
        state.review?.availableActions.includes("VERIFY")
          ? `/payroll/tax-openings/${encodeURIComponent(employee)}/verify?${query}`
          : null
      }
      onRefresh={controller.refresh}
      onFirst={() => onQuery(year, null, "history")}
      onNext={() => {
        if (state.review?.history.nextCursor)
          onQuery(year, state.review.history.nextCursor, "history");
      }}
      onRevision={controller.selectRevision}
      onClose={controller.closeInspection}
      onTab={(value) => {
        if (value === "overview" || value === "history") onQuery(year, after, value);
      }}
    />
  );
}
