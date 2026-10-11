import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { ShiftEditorState } from "../models/shift-editor-state";
import { type ShiftFormValues, shiftFormValues } from "../models/shift-form-values";
import type { ShiftEditorController } from "./shift-editor-controller";

export function useShiftForm(
  controller: ShiftEditorController,
  state: ShiftEditorState,
  timezone: string,
) {
  const values = useMemo(() => shiftFormValues(state.shift, timezone), [state.shift, timezone]);
  const form = useForm<ShiftFormValues>({ values });
  const code = useController({ name: "code", control: form.control });
  const name = useController({ name: "name", control: form.control });
  const startsAt = useController({ name: "startsAt", control: form.control });
  const endsAt = useController({ name: "endsAt", control: form.control });
  const breakMinutes = useController({ name: "breakMinutes", control: form.control });
  const zone = useController({ name: "timezone", control: form.control });
  const mode = useController({ name: "mode", control: form.control });
  const active = useController({ name: "active", control: form.control });
  const locationRequired = useController({ name: "locationRequired", control: form.control });
  const maxAccuracyMeters = useController({ name: "maxAccuracyMeters", control: form.control });
  const fenced = useController({ name: "fenced", control: form.control });
  const latitude = useController({ name: "latitude", control: form.control });
  const longitude = useController({ name: "longitude", control: form.control });
  const radiusMeters = useController({ name: "radiusMeters", control: form.control });
  const reason = useController({ name: "reason", control: form.control });
  const requestDeparture = useNavigationProtection(
    state.stage === "saving"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" && form.formState.isDirty
          ? "dirty"
          : "none",
  );
  return {
    code: code.field,
    name: name.field,
    startsAt: startsAt.field,
    endsAt: endsAt.field,
    breakMinutes: breakMinutes.field,
    timezone: zone.field,
    mode: mode.field,
    active: active.field,
    locationRequired: locationRequired.field,
    maxAccuracyMeters: maxAccuracyMeters.field,
    fenced: fenced.field,
    latitude: latitude.field,
    longitude: longitude.field,
    radiusMeters: radiusMeters.field,
    reason: reason.field,
    editable: state.stage === "editing",
    dirty: form.formState.isDirty,
    refresh: () =>
      requestDeparture(() => {
        form.reset(values);
        void controller.refresh();
      }),
    submit: form.handleSubmit((fields) =>
      controller.save({
        details: {
          code: fields.code,
          name: fields.name,
          startsAt: fields.startsAt,
          endsAt: fields.endsAt,
          breakMinutes:
            fields.breakMinutes.trim() === "" ? Number.NaN : Number(fields.breakMinutes),
          timezone: fields.timezone,
          mode: fields.mode,
          locationRequired: fields.locationRequired === "true",
          maxAccuracyMeters:
            fields.maxAccuracyMeters.trim() === "" ? Number.NaN : Number(fields.maxAccuracyMeters),
          fence:
            fields.locationRequired === "true" && fields.fenced === "true"
              ? {
                  latitude: fields.latitude.trim() === "" ? Number.NaN : Number(fields.latitude),
                  longitude: fields.longitude.trim() === "" ? Number.NaN : Number(fields.longitude),
                  radiusMeters:
                    fields.radiusMeters.trim() === "" ? Number.NaN : Number(fields.radiusMeters),
                }
              : null,
        },
        active: fields.active === "true",
        reason: fields.reason,
      }),
    ),
  };
}
