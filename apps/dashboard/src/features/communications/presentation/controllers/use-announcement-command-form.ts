import { useController, useForm } from "react-hook-form";
import { parseCompanyDateTime } from "../../../../core/presentation/dates/company-date-time";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { AnnouncementCommandState } from "../models/announcement-command-state";
import type { AnnouncementCommandController } from "./announcement-command-controller";

type Fields = { reason: string; scheduled: boolean; localTime: string };
export function useAnnouncementCommandForm(
  controller: AnnouncementCommandController,
  state: AnnouncementCommandState,
  timezone: string,
) {
  const form = useForm<Fields>({ defaultValues: { reason: "", scheduled: false, localTime: "" } });
  const reason = useController({ name: "reason", control: form.control });
  const scheduled = useController({ name: "scheduled", control: form.control });
  const localTime = useController({ name: "localTime", control: form.control });
  const depart = useNavigationProtection(
    state.stage === "saving"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" && form.formState.isDirty
          ? "dirty"
          : "none",
  );
  return {
    reason: reason.field,
    scheduled: scheduled.field,
    localTime: localTime.field,
    localTimeError: localTime.fieldState.error?.message,
    editable: state.stage === "editing",
    submit: form.handleSubmit((fields) => {
      const instant = fields.scheduled ? parseCompanyDateTime(fields.localTime, timezone) : null;
      if (fields.scheduled && instant === null) {
        form.setError("localTime", { type: "validate", message: "invalid_instant" });
        return;
      }
      form.clearErrors("localTime");
      controller.prepare({ reason: fields.reason, scheduledFor: instant });
    }),
    refresh: () =>
      depart(() => {
        void controller.refresh().then(() => {
          if (controller.getSnapshot().stage === "editing") form.reset();
        });
      }),
  };
}
