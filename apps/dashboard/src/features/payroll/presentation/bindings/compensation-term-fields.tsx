import type { Failure } from "../../../../core/domain/result";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { useCompensationTermFields } from "../controllers/use-compensation-term-fields";
import { CompensationDeductionFields } from "../pages/compensation-deduction-fields";
import { CompensationEarningsFields } from "../pages/compensation-earnings-fields";
import { CompensationInsuranceFields } from "../pages/compensation-insurance-fields";
import { CompensationPayBasisFields } from "../pages/compensation-pay-basis-fields";
import { CompensationTaxFields } from "../pages/compensation-tax-fields";
import { FixedEarningFields } from "./fixed-earning-fields";
export function CompensationTermFields({
  form,
  failure,
  locale,
  taxReadOnly = false,
}: {
  form: ReturnType<typeof useCompensationTermFields>;
  failure: Failure | null;
  locale: Locale;
  taxReadOnly?: boolean;
}) {
  return (
    <>
      <CompensationEarningsFields form={form} failure={failure} locale={locale}>
        {form.earnings.map((line, index) => (
          <FixedEarningFields
            key={line.id}
            control={form.control}
            index={index}
            editable={form.editable}
            failure={failure}
            locale={locale}
            onRemove={form.removeEarning}
          />
        ))}
      </CompensationEarningsFields>
      <CompensationTaxFields
        form={form}
        failure={failure}
        locale={locale}
        taxReadOnly={taxReadOnly}
      />
      <CompensationInsuranceFields form={form} failure={failure} locale={locale} />
      <CompensationDeductionFields form={form} failure={failure} locale={locale} />
      <CompensationPayBasisFields form={form} failure={failure} locale={locale} />
    </>
  );
}
