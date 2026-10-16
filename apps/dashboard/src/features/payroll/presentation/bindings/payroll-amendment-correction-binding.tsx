import type { Failure } from "../../../../core/domain/result";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { PayrollAmendmentCorrection } from "../../domain/entities/payroll-amendment";
import type { PayrollAmendmentBasis } from "../../domain/entities/payroll-amendment-preparation";
import { usePayrollAmendmentCorrectionForm } from "../controllers/use-payroll-amendment-correction-form";
import { payrollAmendmentAttendanceChoiceView } from "../models/payroll-amendment-attendance-view";
import { payrollAmendmentCorrectionFailure } from "../models/payroll-amendment-editor-view";
import { PayrollAmendmentCorrectionPanel } from "../pages/payroll-amendment-correction-panel";
import { CompensationTermFields } from "./compensation-term-fields";
import { PayrollInputTermFields } from "./payroll-input-term-fields";
export function PayrollAmendmentCorrectionBinding({
  source,
  correction,
  locale,
  timezone,
  failure,
  correctionIndex,
  onApply,
  onClose,
  onRemove,
  onDirtyChange,
}: {
  source: PayrollAmendmentBasis;
  correction: PayrollAmendmentCorrection | null;
  locale: Locale;
  timezone: string;
  failure: Failure | null;
  correctionIndex: number;
  onApply: (input: PayrollAmendmentCorrection) => void;
  onClose: () => void;
  onRemove: () => void;
  onDirtyChange: (dirty: boolean) => void;
}) {
  const form = usePayrollAmendmentCorrectionForm(
    source,
    correction,
    onApply,
    onClose,
    onDirtyChange,
  );
  return (
    <PayrollAmendmentCorrectionPanel
      form={form}
      attendance={payrollAmendmentAttendanceChoiceView(
        source,
        form.attendance.value,
        locale,
        timezone,
      )}
      locale={locale}
      month={source.month}
      canRemove={correction !== null}
      onRemove={onRemove}
      compensation={
        <CompensationTermFields
          form={form.compensation}
          failure={
            form.compensationFailure ??
            payrollAmendmentCorrectionFailure(failure, correctionIndex, "compensation")
          }
          locale={locale}
          taxReadOnly
        />
      }
      input={
        <PayrollInputTermFields
          form={form.input}
          failure={
            form.inputFailure ??
            payrollAmendmentCorrectionFailure(failure, correctionIndex, "input")
          }
          locale={locale}
        />
      }
    />
  );
}
