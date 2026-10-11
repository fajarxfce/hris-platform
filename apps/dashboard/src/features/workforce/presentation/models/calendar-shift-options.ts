import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { ShiftDefinition } from "../../domain/entities/shift-definition";
import type { ShiftSnapshot } from "../../domain/entities/shift-snapshot";
import { calendarMessages } from "../i18n/calendar-messages";
import { shiftHours } from "./calendar-view";

export const shiftChoiceKey = (shift: ShiftSnapshot | null): string =>
  shift ? `${shift.id}:${shift.revision}` : "";
/** The active page and at most seven selected snapshots; no unbounded catalog cache. */
export function calendarShiftOptions(
  shifts: readonly ShiftDefinition[],
  selected: readonly (ShiftSnapshot | null)[],
  locale: Locale,
) {
  const snapshots = new Map<string, ShiftSnapshot>();
  for (const shift of shifts)
    if (shift.active) {
      const snapshot = Object.freeze({
        id: shift.id,
        revision: shift.version,
        details: shift.details,
      });
      snapshots.set(shiftChoiceKey(snapshot), snapshot);
    }
  for (const shift of selected) if (shift) snapshots.set(shiftChoiceKey(shift), shift);
  const text = calendarMessages(locale);
  return [...snapshots].map(([key, snapshot]) => ({
    key,
    snapshot,
    label: `${snapshot.details.name} (${snapshot.details.code}) · ${shiftHours(snapshot.details, locale)} · ${snapshot.details.timezone} · ${text.revision} ${snapshot.revision}`,
  }));
}
