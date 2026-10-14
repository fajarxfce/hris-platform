import { memo } from "react";
import type { Control } from "react-hook-form";
import type { Failure } from "../../../../core/domain/result";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useFixedEarningForm } from "../controllers/use-fixed-earning-form";
import type { CompensationFormValues } from "../models/compensation-form-values";
import { FixedEarningFieldsPage } from "../pages/fixed-earning-fields-page";
export const FixedEarningFields = memo(function FixedEarningFields(props: {
  control: Control<CompensationFormValues>;
  index: number;
  editable: boolean;
  failure: Failure | null;
  locale: Locale;
  onRemove: (index: number) => void;
}) {
  const form = useFixedEarningForm(props.control, props.index);
  return <FixedEarningFieldsPage {...props} form={form} />;
});
