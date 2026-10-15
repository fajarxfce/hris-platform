import { useMemo } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { payrollPaymentReconciliationSnapshot } from "../../domain/entities/payroll-payment-reconciliation-snapshot";
import { canReconcilePayrollPayment } from "../../domain/policies/payroll-payment-reconciliation-policy";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { usePayrollPayment } from "../controllers/use-payroll-payment";
import {
  payrollPaymentParameters,
  payrollPaymentPath,
  payrollPaymentSearchFromRoute,
  payrollPaymentTab,
  payrollPaymentTabs,
} from "../models/payroll-payment-route";
import { payrollPaymentTransitionActions } from "../models/payroll-payment-transition-view";
import { payrollPaymentView } from "../models/payroll-payment-view";
import { PayrollPaymentPage } from "../pages/payroll-payment-page";
import { PayrollPaymentWorksheet } from "./payroll-payment-worksheet";
export function PayrollPaymentScreen(props: PayrollScreenProps) {
  const { batchId = "" } = useParams();
  const [parameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const directory = payrollPaymentParameters(
    props.access.companyId,
    payrollPaymentSearchFromRoute(parameters, props.access.companyId, today),
  );
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/payments?${directory}`} replace />;
  if (!parameters.has("company")) {
    if (parameters.has("historyAfter"))
      directory.set("historyAfter", parameters.get("historyAfter") ?? "");
    directory.set("view", payrollPaymentTab(parameters.get("view")));
    return <Navigate to={`${payrollPaymentPath(batchId)}?${directory}`} replace />;
  }
  return (
    <PayrollPaymentBinding
      key={`${props.accountId}:${props.access.companyId}:${batchId}`}
      {...props}
      id={batchId}
      directory={directory.toString()}
    />
  );
}
function PayrollPaymentBinding(props: PayrollScreenProps & { id: string; directory: string }) {
  const [parameters, setParameters] = useSearchParams();
  const navigate = useNavigate();
  const after = parameters.get("historyAfter"),
    tab = payrollPaymentTab(parameters.get("view"));
  const selectedItem = tab === "instructions" || tab === "results" ? parameters.get("item") : null;
  const selectedChange = tab === "history" ? parameters.get("change") : null;
  const state = usePayrollPayment(
    props.accountId,
    props.access,
    props.id,
    after,
    props.payroll.loadPayment,
  );
  const view = useMemo(
    () =>
      state.review
        ? payrollPaymentView(
            state.review,
            selectedItem,
            selectedChange,
            props.locale,
            props.timezone,
          )
        : null,
    [state.review, selectedItem, selectedChange, props.locale, props.timezone],
  );
  useWorkspaceRevalidation(state.failure);
  const transitions = state.review
    ? payrollPaymentTransitionActions(props.access, state.review, props.locale)
    : [];
  const canReconcile =
    state.review !== null &&
    canReconcilePayrollPayment(props.access, payrollPaymentReconciliationSnapshot(state.review));
  return (
    <PayrollPaymentPage
      state={state}
      view={view}
      transitions={transitions}
      canReconcile={canReconcile}
      returnsTo={
        view?.itemAssessmentId
          ? `/payroll/payslips/${view.itemAssessmentId}/returns?${new URLSearchParams({
              company: props.access.companyId,
              batch: props.id,
              payment: selectedItem ?? "",
            })}`
          : null
      }
      onReconcile={() => {
        if (canReconcile) navigate(`${payrollPaymentPath(props.id)}/reconcile?${parameters}`);
      }}
      onTransition={(intent) => {
        if (transitions.some((item) => item.intent === intent))
          navigate(`${payrollPaymentPath(props.id)}/${intent}?${parameters}`);
      }}
      locale={props.locale}
      companyName={props.companyName}
      backTo={`/payroll/payments?${props.directory}`}
      tab={tab}
      firstHistory={after === null}
      worksheet={
        state.review?.availableActions.includes("EXPORT") ? (
          <PayrollPaymentWorksheet
            download={props.payroll.downloadPayment}
            access={props.access}
            review={state.review}
            locale={props.locale}
            onUnavailable={state.withdrawReview}
          />
        ) : null
      }
      onTab={(value) => {
        if (!payrollPaymentTabs.some((item) => item === value)) return;
        const next = new URLSearchParams(parameters);
        next.set("view", value);
        next.delete("item");
        next.delete("change");
        setParameters(next);
      }}
      onItem={(id) => {
        const next = new URLSearchParams(parameters);
        if (id === null) next.delete("item");
        else if (state.review?.batch.items.some((item) => item.id === id)) next.set("item", id);
        setParameters(next, { replace: true });
      }}
      onChange={(version) => {
        const next = new URLSearchParams(parameters);
        if (version === null) next.delete("change");
        else if (state.review?.history.items.some((item) => String(item.version) === version))
          next.set("change", version);
        setParameters(next, { replace: true });
      }}
      onFirstHistory={() => {
        const next = new URLSearchParams(parameters);
        next.delete("historyAfter");
        next.delete("change");
        setParameters(next);
      }}
      onNextHistory={() => {
        if (!state.review?.history.nextCursor) return;
        const next = new URLSearchParams(parameters);
        next.set("historyAfter", state.review.history.nextCursor);
        next.delete("change");
        setParameters(next);
      }}
    />
  );
}
