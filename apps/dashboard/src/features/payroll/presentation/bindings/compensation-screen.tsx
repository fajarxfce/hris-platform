import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canChangeCompensation } from "../../domain/policies/compensation-access-policy";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { CompensationController } from "../controllers/compensation-controller";
import { usePayrollPolicyMonth } from "../controllers/use-payroll-policy-month";
import {
  compensationEmployeeParameters,
  compensationReviewParameters,
} from "../models/compensation-route";
import {
  compensationEvidenceView,
  compensationHistoryView,
  compensationView,
} from "../models/compensation-view";
import { CompensationPage } from "../pages/compensation-page";
export function CompensationScreen(props: PayrollScreenProps) {
  const [parameters, setParameters] = useSearchParams(),
    { employeeId = "" } = useParams();
  const currentMonth = useMemo(
    () => companyDate(props.timezone, new Date()).slice(0, 7),
    [props.timezone],
  );
  const month = parameters.get("month") ?? currentMonth,
    after = parameters.get("historyAfter");
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        to={`/payroll/compensation?${compensationEmployeeParameters(props.access.companyId)}`}
        replace
      />
    );
  const canonical = compensationReviewParameters(props.access.companyId, month, after);
  if (!parameters.has("company") || !parameters.has("month"))
    return (
      <Navigate
        to={`/payroll/compensation/${encodeURIComponent(employeeId)}?${canonical}`}
        replace
      />
    );
  return (
    <CompensationBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}`}
      {...props}
      employee={employeeId}
      month={month}
      after={after}
      editTo={`/payroll/compensation/${encodeURIComponent(employeeId)}/edit?${compensationReviewParameters(props.access.companyId, month)}`}
      backTo={`/payroll/compensation?${compensationEmployeeParameters(props.access.companyId)}`}
      onQuery={(month, after) =>
        setParameters(compensationReviewParameters(props.access.companyId, month, after))
      }
    />
  );
}
function CompensationBinding({
  payroll,
  access,
  employee,
  month,
  after,
  editTo,
  backTo,
  locale,
  timezone,
  companyName,
  onQuery,
}: PayrollScreenProps & {
  employee: string;
  month: string;
  after: string | null;
  editTo: string;
  backTo: string;
  onQuery: (month: string, after: string | null) => void;
}) {
  const controller = useMemo(
    () =>
      new CompensationController(payroll.loadCompensationReview, access, employee, month, after),
    [payroll.loadCompensationReview, access, employee, month, after],
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
  const filter = usePayrollPolicyMonth(month, (month) => onQuery(month, null));
  const effective = useMemo(
    () => (state.review?.effective ? compensationView(state.review.effective, locale) : null),
    [state.review, locale],
  );
  const selected = useMemo(
    () => (state.selected ? compensationView(state.selected.compensation, locale) : null),
    [state.selected, locale],
  );
  const evidence = useMemo(
    () =>
      state.selected?.evidence
        ? compensationEvidenceView(state.selected.evidence, locale, timezone)
        : null,
    [state.selected, locale, timezone],
  );
  const history = useMemo(
    () => (state.review ? compensationHistoryView(state.review, locale, timezone) : []),
    [state.review, locale, timezone],
  );
  return (
    <CompensationPage
      state={state}
      effective={effective}
      selected={selected}
      evidence={evidence}
      history={history}
      filter={filter}
      locale={locale}
      companyName={companyName}
      timezone={timezone}
      editTo={
        state.stage === "ready" &&
        state.review?.canChange &&
        canChangeCompensation(access.permissions)
          ? editTo
          : null
      }
      backTo={backTo}
      firstHistory={after === null}
      onRefresh={controller.refresh}
      onFirst={() => onQuery(month, null)}
      onNext={() => {
        if (state.review?.history.nextCursor) onQuery(month, state.review.history.nextCursor);
      }}
      onRevision={controller.selectRevision}
      onInspect={controller.inspectConfiguration}
      onClose={controller.closeInspection}
    />
  );
}
