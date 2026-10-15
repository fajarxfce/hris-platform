import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollPayslipScope } from "../../domain/entities/payroll-payslip";
import { canReadPayrollPayments } from "../../domain/policies/payroll-payment-read-policy";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollPayslipController } from "../controllers/payroll-payslip-controller";
import {
  payrollPayslipParameters,
  payrollPayslipScopeFromRoute,
  payrollPayslipSearchFromRoute,
} from "../models/payroll-payslip-route";
import {
  payrollPayslipPaymentRows,
  payrollPayslipSettlementView,
  payrollPayslipView,
} from "../models/payroll-payslip-view";
import { PayrollPayslipPage } from "../pages/payroll-payslip-page";

export function PayrollPayslipScreen(props: PayrollScreenProps) {
  const [parameters] = useSearchParams(),
    { payslipId = "" } = useParams();
  const scope = payrollPayslipScopeFromRoute(
    parameters,
    props.access.permissions.includes("payroll.read"),
  );
  if (parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/payslips?company=${props.access.companyId}`} replace />;
  return (
    <PayrollPayslipBinding
      key={`${props.accountId}:${props.access.companyId}:${payslipId}:${scope}`}
      {...props}
      id={payslipId.toLowerCase()}
      scope={scope}
    />
  );
}
function PayrollPayslipBinding(
  props: PayrollScreenProps & { id: string; scope: PayrollPayslipScope },
) {
  const [parameters, setParameters] = useSearchParams();
  const { payroll, access, id, scope, locale, timezone } = props;
  const controller = useMemo(
    () => new PayrollPayslipController(payroll, access, id, scope),
    [payroll, access, id, scope],
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
  useWorkspaceRevalidation(state.failure ?? state.paymentFailure ?? state.downloadFailure);
  const today = useMemo(() => companyDate(timezone, new Date()), [timezone]);
  const backSearch = payrollPayslipSearchFromRoute(
    parameters,
    access.companyId,
    today,
    access.permissions.includes("payroll.read"),
  );
  const view = useMemo(
    () => (state.payslip ? payrollPayslipView(state.payslip, locale, timezone) : null),
    [state.payslip, locale, timezone],
  );
  const settlement = useMemo(
    () => payrollPayslipSettlementView(state.payments, locale),
    [state.payments, locale],
  );
  const paymentRows = useMemo(
    () => payrollPayslipPaymentRows(state.payments, locale, timezone),
    [state.payments, locale, timezone],
  );
  return (
    <PayrollPayslipPage
      state={state}
      view={view}
      paymentRows={paymentRows}
      settlement={settlement}
      returnsTo={
        state.payslip && canReadPayrollPayments(access.permissions)
          ? `/payroll/payslips/${id}/returns?${new URLSearchParams({ company: access.companyId, source: "payslip", scope })}`
          : null
      }
      locale={locale}
      timezone={timezone}
      companyName={props.companyName}
      tab={parameters.get("tab") === "payments" ? "payments" : "calculation"}
      backTo={`/payroll/payslips?${payrollPayslipParameters(access.companyId, backSearch)}`}
      onRefresh={controller.refresh}
      onRefreshPayments={controller.refreshPayments}
      onDownload={controller.download}
      onLanguage={(language) => {
        if (language === "ID" || language === "EN") controller.selectDocumentLanguage(language);
      }}
      onTab={(tab) => {
        const next = new URLSearchParams(parameters);
        next.set("tab", tab === "payments" ? "payments" : "calculation");
        setParameters(next);
      }}
    />
  );
}
