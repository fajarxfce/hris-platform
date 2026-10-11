import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { ShiftDefinition } from "../../domain/entities/shift-definition";
import type { ShiftPage } from "../../domain/entities/shift-page";
import { shiftMessages } from "../i18n/shift-messages";

export function shiftDirectoryView(page: ShiftPage, locale: Locale) {
  const text = shiftMessages(locale);
  return page.items.map((shift) => ({
    id: shift.id,
    actionLabel: `${shift.details.name} (${shift.details.code})`,
    cells: [
      shift.details.code,
      shift.details.name,
      `${shift.details.startsAt}–${shift.details.endsAt}`,
      text[shift.details.mode],
      shift.active ? text.active : text.inactive,
    ],
  }));
}
export function shiftView(shift: ShiftDefinition, locale: Locale) {
  const text = shiftMessages(locale);
  const number = new Intl.NumberFormat(locale, { maximumFractionDigits: 6 });
  const details = shift.details;
  return {
    overview: [
      { label: text.code, value: details.code },
      { label: text.name, value: details.name },
      { label: text.status, value: shift.active ? text.active : text.inactive },
      { label: text.startsAt, value: details.startsAt },
      { label: text.endsAt, value: details.endsAt },
      { label: text.breakMinutes, value: number.format(details.breakMinutes) },
      { label: text.timezone, value: details.timezone },
      { label: text.mode, value: text[details.mode] },
      { label: text.version, value: number.format(shift.version) },
    ],
    location: [
      { label: text.locationRequired, value: details.locationRequired ? text.yes : text.no },
      { label: text.maxAccuracy, value: number.format(details.maxAccuracyMeters) },
      ...(details.fence
        ? [
            { label: text.latitude, value: number.format(details.fence.latitude) },
            { label: text.longitude, value: number.format(details.fence.longitude) },
            { label: text.radius, value: number.format(details.fence.radiusMeters) },
          ]
        : [{ label: text.fenced, value: text.noFence }]),
    ],
  };
}
