import type { Failure } from "../../../../core/domain/result";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { usePayrollInputTermFields } from "../controllers/use-payroll-input-term-fields";
import { PayrollInputHolidayFields } from "../pages/payroll-input-holiday-fields";
import { PayrollInputTermsFields } from "../pages/payroll-input-terms-fields";
import {
  PayrollInputDayFields,
  PayrollInputDeductionFields,
  PayrollInputEarningFields,
} from "./payroll-input-line-fields";
export function PayrollInputTermFields({
  form,
  failure,
  locale,
}: {
  form: ReturnType<typeof usePayrollInputTermFields>;
  failure: Failure | null;
  locale: Locale;
}) {
  return (
    <>
      <PayrollInputTermsFields
        form={form}
        failure={failure}
        locale={locale}
        earnings={form.earnings.map((row, index) => (
          <PayrollInputEarningFields
            key={row.id}
            control={form.control}
            index={index}
            editable={form.editable}
            failure={failure}
            locale={locale}
            onRemove={form.removeEarning}
          />
        ))}
        deductions={form.deductions.map((row, index) => (
          <PayrollInputDeductionFields
            key={row.id}
            control={form.control}
            index={index}
            editable={form.editable}
            failure={failure}
            locale={locale}
            onRemove={form.removeDeduction}
          />
        ))}
        days={form.days.map((row, index) => (
          <PayrollInputDayFields
            key={row.id}
            control={form.control}
            index={index}
            editable={form.editable}
            failure={failure}
            locale={locale}
            onRemove={form.removeDay}
          />
        ))}
      />
      <PayrollInputHolidayFields form={form} failure={failure} locale={locale} />
    </>
  );
}
