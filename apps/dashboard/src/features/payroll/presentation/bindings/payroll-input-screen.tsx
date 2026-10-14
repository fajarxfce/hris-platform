import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollInputController } from "../controllers/payroll-input-controller";
import { usePayrollPolicyMonth } from "../controllers/use-payroll-policy-month";
import { payrollInputReviewParameters } from "../models/payroll-input-route";
import { payrollInputReviewView, payrollInputView } from "../models/payroll-input-view";
import { PayrollInputPage } from "../pages/payroll-input-page";
export function PayrollInputScreen(props: PayrollScreenProps) {
  const [parameters, setParameters] = useSearchParams(),
    { employeeId = "" } = useParams();
  const today = useMemo(
    () => companyDate(props.timezone, new Date()).slice(0, 7),
    [props.timezone],
  );
  const month = parameters.get("month") ?? today,
    after = parameters.get("historyAfter"),
    tab = parameters.get("tab") === "history" ? "history" : "overview";
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/inputs?company=${props.access.companyId}`} replace />;
  const canonical = payrollInputReviewParameters(props.access.companyId, month, after, tab);
  if (!parameters.has("company") || !parameters.has("month"))
    return (
      <Navigate to={`/payroll/inputs/${encodeURIComponent(employeeId)}?${canonical}`} replace />
    );
  return (
    <PayrollInputBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}`}
      {...props}
      employee={employeeId}
      month={month}
      after={after}
      tab={tab}
      onQuery={(month, after, tab) =>
        setParameters(payrollInputReviewParameters(props.access.companyId, month, after, tab))
      }
    />
  );
}
function PayrollInputBinding({
  payroll,
  access,
  employee,
  month,
  after,
  tab,
  locale,
  timezone,
  companyName,
  onQuery,
}: PayrollScreenProps & {
  employee: string;
  month: string;
  after: string | null;
  tab: string;
  onQuery: (month: string, after: string | null, tab: string) => void;
}) {
  const controller = useMemo(
    () => new PayrollInputController(payroll, access, employee, month, after),
    [payroll, access, employee, month, after],
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
  const filter = usePayrollPolicyMonth(month, (value) => onQuery(value, null, "overview"));
  const view = useMemo(
    () => (state.review?.current ? payrollInputView(state.review.current, locale, timezone) : null),
    [state.review, locale, timezone],
  );
  const review = useMemo(
    () => (state.review ? payrollInputReviewView(state.review, locale, timezone) : null),
    [state.review, locale, timezone],
  );
  const selected = useMemo(
    () => (state.selected ? payrollInputView(state.selected, locale, timezone) : null),
    [state.selected, locale, timezone],
  );
  const query = payrollInputReviewParameters(access.companyId, month);
  return (
    <PayrollInputPage
      state={state}
      view={view}
      review={review}
      selected={selected}
      filter={filter}
      locale={locale}
      companyName={companyName}
      tab={tab}
      first={after === null}
      backTo={`/payroll/inputs?${query}`}
      editTo={
        state.review?.availableActions.includes("PREPARE")
          ? `/payroll/inputs/${encodeURIComponent(employee)}/edit?${query}`
          : null
      }
      verifyTo={
        state.review?.availableActions.includes("VERIFY")
          ? `/payroll/inputs/${encodeURIComponent(employee)}/verify?${query}`
          : null
      }
      onRefresh={controller.refresh}
      onFirst={() => onQuery(month, null, "history")}
      onNext={() => {
        if (state.review?.history.nextCursor)
          onQuery(month, state.review.history.nextCursor, "history");
      }}
      onRevision={controller.selectRevision}
      onClose={controller.closeInspection}
      onTab={(value) => {
        if (value === "overview" || value === "history") onQuery(month, after, value);
      }}
    />
  );
}
