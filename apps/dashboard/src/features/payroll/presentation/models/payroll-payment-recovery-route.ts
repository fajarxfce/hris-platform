import { isUuid } from "../../../../core/domain/identifiers";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  canReadPayrollPayslips,
  validPayrollPayslipRevision,
} from "../../domain/policies/payroll-payslip-policy";
import { payrollPayslipRevisionFromRoute } from "./payroll-payslip-route";

export function payrollPaymentRecoveryRoute(
  assessment: string,
  access: CompanyAccess,
  input: URLSearchParams,
) {
  const parameters = new URLSearchParams({ company: access.companyId }),
    path = `/payroll/payslips/${encodeURIComponent(assessment)}/returns`,
    batch = input.get("batch"),
    payment = input.get("payment");
  let backTo = `/payroll/payments?${parameters}`,
    backLabel: "backBatch" | "backPayslip" | "backPayments" = "backPayments";
  if (batch && isUuid(batch)) {
    parameters.set("batch", batch.toLowerCase());
    const back = new URLSearchParams({ company: access.companyId, view: "results" });
    if (payment && isUuid(payment)) {
      parameters.set("payment", payment.toLowerCase());
      back.set("item", payment.toLowerCase());
    }
    backTo = `/payroll/payments/${batch.toLowerCase()}?${back}`;
    backLabel = "backBatch";
  } else if (input.get("source") === "payslip" && canReadPayrollPayslips(access.permissions)) {
    parameters.set("source", "payslip");
    parameters.set("scope", input.get("scope") === "SELF" ? "SELF" : "AUTHORIZED");
    const revision = payrollPayslipRevisionFromRoute(input);
    if (revision !== null && validPayrollPayslipRevision(revision))
      parameters.set("revision", String(revision));
    const back = new URLSearchParams(parameters);
    back.delete("source");
    back.set("tab", "payments");
    backTo = `/payroll/payslips/${encodeURIComponent(assessment)}?${back}`;
    backLabel = "backPayslip";
  }
  return {
    review: `${path}?${parameters}`,
    record: `${path}/new?${parameters}`,
    backTo,
    backLabel,
  };
}
