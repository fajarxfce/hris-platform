import { useEffect, useReducer } from "react";
import { useController, useForm, useWatch } from "react-hook-form";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { LeaveBatchKind } from "../../domain/entities/leave-batch";
import type { LeavePolicyDefinition } from "../../domain/entities/leave-policy-definition";
import { initialLeaveBatchPeriod } from "../models/leave-batch-route";
import type { LeaveBatchStartState } from "../models/leave-batch-state";
import type { LeaveBatchStartController } from "./leave-batch-start-controller";

type Values = { type: LeavePolicyDefinition | null; period: string; reason: string };
export function useLeaveBatchForm(
  controller: LeaveBatchStartController,
  state: LeaveBatchStartState,
  companyId: CompanyId,
  kind: LeaveBatchKind,
  today: string,
) {
  const initialPeriod = initialLeaveBatchPeriod(today, kind);
  const form = useForm<Values>({
    defaultValues: {
      type: null,
      period: kind === "YEAR_CLOSE" ? initialPeriod.slice(0, 4) : initialPeriod,
      reason: "",
    },
  });
  const period = useController({ name: "period", control: form.control }),
    reason = useController({ name: "reason", control: form.control });
  const type = useWatch({ name: "type", control: form.control });
  const [pickerOpen, setPickerOpen] = useReducer((_old: boolean, next: boolean) => next, false);
  const { reset, setValue } = form;
  useEffect(() => {
    if (
      state.stage === "saved" ||
      state.stage === "unavailable" ||
      (state.stage === "unconfirmed" && state.review === null)
    ) {
      reset({ type: null, period: "", reason: "" });
      setPickerOpen(false);
    }
  }, [state.stage, state.review, reset]);
  const depart = useNavigationProtection(
    state.stage === "saving" || state.stage === "preparing"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" && form.formState.isDirty
          ? "dirty"
          : "none",
  );
  return {
    period: period.field,
    reason: reason.field,
    type,
    pickerOpen,
    chooseType: () => {
      if (state.stage === "editing") setPickerOpen(true);
    },
    closePicker: () => setPickerOpen(false),
    selectType: (value: LeavePolicyDefinition) => {
      if (state.stage === "editing" && value.companyId === companyId)
        setValue("type", value, { shouldDirty: true });
      setPickerOpen(false);
    },
    prepare: form.handleSubmit((fields) =>
      controller.prepare({
        kind,
        typeId: fields.type?.id ?? "",
        period: kind === "YEAR_CLOSE" ? `${fields.period}-12` : fields.period,
      }),
    ),
    submit: form.handleSubmit((fields) => controller.save(fields.reason)),
    revise: controller.revise,
    reload: () =>
      depart(() => {
        reset({
          type: null,
          period: kind === "YEAR_CLOSE" ? initialPeriod.slice(0, 4) : initialPeriod,
          reason: "",
        });
        setPickerOpen(false);
        controller.refresh();
      }),
  };
}
