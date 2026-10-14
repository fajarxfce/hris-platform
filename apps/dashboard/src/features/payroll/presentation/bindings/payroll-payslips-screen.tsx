import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollPayslipSearch } from "../../domain/entities/payroll-payslip";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollPayslipsController } from "../controllers/payroll-payslips-controller";
import { usePayrollPayslipFilter } from "../controllers/use-payroll-payslip-filter";
import {
  payrollPayslipParameters,
  payrollPayslipSearchFromRoute,
} from "../models/payroll-payslip-route";
import { payrollPayslipRows } from "../models/payroll-payslip-view";
import { PayrollPayslipsPage } from "../pages/payroll-payslips-page";

export function PayrollPayslipsScreen(props: PayrollScreenProps) {
  const [parameters, setParameters] = useSearchParams();
  const companyReader = props.access.permissions.includes("payroll.read");
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const search = useMemo(
    () => payrollPayslipSearchFromRoute(parameters, props.access.companyId, today, companyReader),
    [parameters, props.access.companyId, today, companyReader],
  );
  if (
    parameters.get("company") !== props.access.companyId ||
    !parameters.has("from") ||
    !parameters.has("until") ||
    parameters.get("scope") !== search.scope
  )
    return (
      <Navigate
        to={`/payroll/payslips?${payrollPayslipParameters(props.access.companyId, search)}`}
        replace
      />
    );
  return (
    <PayrollPayslipsBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      companyReader={companyReader}
      search={search}
      onSearch={(search) => setParameters(payrollPayslipParameters(props.access.companyId, search))}
    />
  );
}
function PayrollPayslipsBinding(
  props: PayrollScreenProps & {
    companyReader: boolean;
    search: PayrollPayslipSearch;
    onSearch: (search: PayrollPayslipSearch) => void;
  },
) {
  const navigate = useNavigate(),
    { payroll, access, search, locale } = props;
  const controller = useMemo(
    () => new PayrollPayslipsController(payroll.listPayslips, access, search),
    [payroll.listPayslips, access, search],
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
  const filter = usePayrollPayslipFilter(search, props.onSearch);
  const rows = useMemo(
    () => payrollPayslipRows(state.page?.items ?? [], locale),
    [state.page, locale],
  );
  return (
    <PayrollPayslipsPage
      state={state}
      filter={filter}
      rows={rows}
      locale={locale}
      companyName={props.companyName}
      companyReader={props.companyReader}
      firstPage={search.after === null}
      onRefresh={controller.refresh}
      onFirst={() => props.onSearch({ ...search, after: null })}
      onNext={() => {
        if (state.page?.nextCursor) props.onSearch({ ...search, after: state.page.nextCursor });
      }}
      onOpen={(id) => {
        if (state.page?.items.some((item) => item.id === id))
          navigate(
            `/payroll/payslips/${encodeURIComponent(id)}?${payrollPayslipParameters(access.companyId, search)}`,
          );
      }}
    />
  );
}
