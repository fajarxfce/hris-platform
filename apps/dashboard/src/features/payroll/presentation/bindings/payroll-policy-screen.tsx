import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canManagePayrollPolicy } from "../../domain/policies/payroll-access-policy";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollPolicyController } from "../controllers/payroll-policy-controller";
import { usePayrollPolicyMonth } from "../controllers/use-payroll-policy-month";
import { payrollPolicyParameters, payrollPolicyQuery } from "../models/payroll-policy-route";
import {
  payrollPolicyEvidenceView,
  payrollPolicyHistoryView,
  payrollPolicyView,
} from "../models/payroll-policy-view";
import { PayrollPolicyPage } from "../pages/payroll-policy-page";

export function PayrollPolicyScreen(props: PayrollScreenProps) {
  const [parameters, setParameters] = useSearchParams();
  const currentMonth = useMemo(
    () => companyDate(props.timezone, new Date()).slice(0, 7),
    [props.timezone],
  );
  const query = payrollPolicyQuery(parameters, props.access.companyId, currentMonth);
  const canonical = payrollPolicyParameters(props.access.companyId, query.month, query.after);
  if (
    !parameters.has("company") ||
    parameters.get("company") !== props.access.companyId ||
    !parameters.has("month")
  )
    return <Navigate to={`/payroll/policy?${canonical}`} replace />;
  return (
    <PayrollPolicyBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      month={query.month}
      after={query.after}
      editTo={`/payroll/policy/edit?${canonical}`}
      onQuery={(month, after) =>
        setParameters(payrollPolicyParameters(props.access.companyId, month, after))
      }
    />
  );
}
function PayrollPolicyBinding({
  payroll,
  access,
  month,
  after,
  editTo,
  locale,
  timezone,
  companyName,
  onQuery,
}: PayrollScreenProps & {
  month: string;
  after: string | null;
  editTo: string;
  onQuery: (month: string, after: string | null) => void;
}) {
  const controller = useMemo(
    () => new PayrollPolicyController(payroll.loadPolicy, access, month, after),
    [payroll.loadPolicy, access, month, after],
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
  const filter = usePayrollPolicyMonth(month, (selected) => onQuery(selected, null));
  const effective = useMemo(
    () => (state.review?.effective ? payrollPolicyView(state.review.effective, locale) : null),
    [state.review, locale],
  );
  const selected = useMemo(
    () => (state.selected ? payrollPolicyView(state.selected.policy, locale) : null),
    [state.selected, locale],
  );
  const evidence = useMemo(
    () =>
      state.selected?.evidence
        ? payrollPolicyEvidenceView(state.selected.evidence, locale, timezone)
        : null,
    [state.selected, locale, timezone],
  );
  const history = useMemo(
    () => (state.review ? payrollPolicyHistoryView(state.review, locale, timezone) : []),
    [state.review, locale, timezone],
  );
  return (
    <PayrollPolicyPage
      state={state}
      effective={effective}
      selected={selected}
      evidence={evidence}
      history={history}
      filter={filter}
      locale={locale}
      companyName={companyName}
      timezone={timezone}
      editTo={state.stage === "ready" && canManagePayrollPolicy(access.permissions) ? editTo : null}
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
