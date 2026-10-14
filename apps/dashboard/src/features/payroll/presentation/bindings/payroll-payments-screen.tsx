import { useMemo } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollPaymentSearchInput } from "../../domain/entities/payroll-payment-search";
import { canReadPayrollPayments } from "../../domain/policies/payroll-payment-read-policy";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { usePayrollPaymentFilters } from "../controllers/use-payroll-payment-filters";
import { usePayrollPayments } from "../controllers/use-payroll-payments";
import {
  payrollPaymentParameters,
  payrollPaymentPath,
  payrollPaymentSearchFromRoute,
} from "../models/payroll-payment-route";
import { payrollPaymentRows } from "../models/payroll-payment-view";
import { PayrollPaymentsPage } from "../pages/payroll-payments-page";
export function PayrollPaymentsScreen(props: PayrollScreenProps) {
  const [parameters, setParameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const search = useMemo(
    () => payrollPaymentSearchFromRoute(parameters, props.access.companyId, today),
    [parameters, props.access.companyId, today],
  );
  if (parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        to={`/payroll/payments?${payrollPaymentParameters(props.access.companyId, search)}`}
        replace
      />
    );
  return (
    <PayrollPaymentsBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      search={search}
      onSearch={(next) => setParameters(payrollPaymentParameters(props.access.companyId, next))}
    />
  );
}
function PayrollPaymentsBinding(
  props: PayrollScreenProps & {
    search: PayrollPaymentSearchInput;
    onSearch: (search: PayrollPaymentSearchInput) => void;
  },
) {
  const navigate = useNavigate();
  const state = usePayrollPayments(
    props.accountId,
    props.access,
    props.search,
    props.payroll.loadPayments,
  );
  const filters = usePayrollPaymentFilters(props.search, props.locale, props.onSearch);
  const rows = useMemo(
    () => (state.page ? payrollPaymentRows(state.page, props.locale, props.timezone) : []),
    [state.page, props.locale, props.timezone],
  );
  useWorkspaceRevalidation(state.failure);
  return (
    <PayrollPaymentsPage
      state={state}
      filters={filters}
      rows={rows}
      locale={props.locale}
      companyName={props.companyName}
      canPrepare={canReadPayrollPayments(props.access.permissions)}
      onPrepare={() =>
        navigate(
          `/payroll/payments/new?${payrollPaymentParameters(props.access.companyId, props.search)}`,
        )
      }
      firstPage={props.search.after === null}
      onFirst={() => props.onSearch({ ...props.search, after: null })}
      onNext={() => {
        if (state.page?.nextCursor)
          props.onSearch({ ...props.search, after: state.page.nextCursor });
      }}
      onOpen={(id) => {
        if (state.page?.items.some((batch) => batch.id === id))
          navigate(
            `${payrollPaymentPath(id)}?${payrollPaymentParameters(props.access.companyId, props.search)}`,
          );
      }}
    />
  );
}
