import {
  Calendar20Regular,
  ClipboardTaskListLtr20Regular,
  DocumentCheckmark20Regular,
  DocumentEdit20Regular,
  DocumentTable20Regular,
  MoneySettings20Regular,
  Payment20Regular,
  PersonMoney20Regular,
  Receipt20Regular,
} from "@fluentui/react-icons";
import type { AppNavigationItem } from "../../../../core/presentation/components/app-portal-shell";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadCompensation } from "../../domain/policies/compensation-access-policy";
import { canReadPayrollPolicy } from "../../domain/policies/payroll-access-policy";
import { canReadPayrollAmendments } from "../../domain/policies/payroll-amendment-access-policy";
import { canReadPayrollFinalizations } from "../../domain/policies/payroll-finalization-policy";
import { canReadPayrollInputs } from "../../domain/policies/payroll-input-access-policy";
import { canReadPayrollPayments } from "../../domain/policies/payroll-payment-read-policy";
import { canReadPayrollPayslips } from "../../domain/policies/payroll-payslip-policy";
import { canReadPayrollPeriods } from "../../domain/policies/payroll-period-policy";
import { canReadTaxOpenings } from "../../domain/policies/tax-opening-access-policy";
import { compensationMessages } from "../i18n/compensation-messages";
import { payrollAmendmentMessages } from "../i18n/payroll-amendment-messages";
import { payrollFinalizationMessages } from "../i18n/payroll-finalization-messages";
import { payrollInputMessages } from "../i18n/payroll-input-messages";
import { payrollPaymentMessages } from "../i18n/payroll-payment-messages";
import { payrollPayslipMessages } from "../i18n/payroll-payslip-messages";
import { payrollPeriodMessages } from "../i18n/payroll-period-messages";
import { payrollPolicyMessages } from "../i18n/payroll-policy-messages";
import { taxOpeningMessages } from "../i18n/tax-opening-messages";

export function payrollNavigation(
  access: CompanyAccess | null,
  locale: Locale,
): readonly AppNavigationItem[] {
  const amendments: readonly AppNavigationItem[] = canReadPayrollAmendments(
    access?.permissions ?? [],
  )
    ? [
        {
          to: "/payroll/amendments",
          label: payrollAmendmentMessages(locale).title,
          icon: <DocumentEdit20Regular />,
        },
      ]
    : [];
  const payments: readonly AppNavigationItem[] = canReadPayrollPayments(access?.permissions ?? [])
    ? [
        {
          to: "/payroll/payments",
          label: payrollPaymentMessages(locale).title,
          icon: <Payment20Regular />,
        },
      ]
    : [];
  const payslips: readonly AppNavigationItem[] = canReadPayrollPayslips(access?.permissions ?? [])
    ? [
        {
          to: "/payroll/payslips",
          label: payrollPayslipMessages(locale).title,
          icon: <Receipt20Regular />,
        },
      ]
    : [];
  const publications: readonly AppNavigationItem[] = canReadPayrollFinalizations(
    access?.permissions ?? [],
  )
    ? [
        {
          to: "/payroll/publications",
          label: payrollFinalizationMessages(locale).title,
          icon: <DocumentCheckmark20Regular />,
        },
      ]
    : [];
  const openings: readonly AppNavigationItem[] = canReadTaxOpenings(access?.permissions ?? [])
    ? [
        {
          to: "/payroll/tax-openings",
          label: taxOpeningMessages(locale).title,
          icon: <DocumentTable20Regular />,
        },
      ]
    : [];
  const inputs: readonly AppNavigationItem[] = canReadPayrollInputs(access?.permissions ?? [])
    ? [
        {
          to: "/payroll/inputs",
          label: payrollInputMessages(locale).title,
          icon: <ClipboardTaskListLtr20Regular />,
        },
      ]
    : [];
  const periods: readonly AppNavigationItem[] = canReadPayrollPeriods(access?.permissions ?? [])
    ? [
        {
          to: "/payroll/periods",
          label: payrollPeriodMessages(locale).title,
          icon: <Calendar20Regular />,
        },
      ]
    : [];
  const policies: readonly AppNavigationItem[] = canReadPayrollPolicy(access?.permissions ?? [])
    ? [
        {
          to: "/payroll/policy",
          label: payrollPolicyMessages(locale).title,
          icon: <MoneySettings20Regular />,
        },
      ]
    : [];
  return canReadCompensation(access?.permissions ?? [])
    ? [
        ...periods,
        ...publications,
        ...payslips,
        ...payments,
        ...amendments,
        ...inputs,
        ...openings,
        ...policies,
        {
          to: "/payroll/compensation",
          label: compensationMessages(locale).title,
          icon: <PersonMoney20Regular />,
        },
      ]
    : [
        ...periods,
        ...publications,
        ...payslips,
        ...payments,
        ...amendments,
        ...inputs,
        ...openings,
        ...policies,
      ];
}
