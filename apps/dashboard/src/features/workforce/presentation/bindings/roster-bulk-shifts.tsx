import { useMemo } from "react";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { ShiftCatalogControls } from "../components/shift-catalog-controls";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import type { useRosterBulkForm } from "../controllers/use-roster-bulk-form";
import { useShiftCatalog } from "../controllers/use-shift-catalog";
import { rosterBulkMessages } from "../i18n/roster-bulk-messages";
import { calendarShiftOptions, shiftChoiceKey } from "../models/calendar-shift-options";

export function RosterBulkShifts(
  props: WorkforceScreenProps & { form: ReturnType<typeof useRosterBulkForm> },
) {
  const catalog = useShiftCatalog(props.accountId, props.access, props.workforce.loadShifts, true);
  const options = useMemo(
    () => calendarShiftOptions(catalog.shifts, [props.form.shift], props.locale),
    [catalog.shifts, props.form.shift, props.locale],
  );
  const text = rosterBulkMessages(props.locale);
  useWorkspaceRevalidation(catalog.failure);
  return (
    <section className="app-roster-bulk-shifts" aria-label={text.shift}>
      <ShiftCatalogControls catalog={catalog} locale={props.locale} disabled={false} />
      <AppSelect
        label={text.shift}
        value={shiftChoiceKey(props.form.shift)}
        error={props.form.invalidShift ? text.chooseShift : undefined}
        onChange={(event) => {
          const option = options.find((value) => value.key === event.target.value);
          if (event.target.value === "" || option) props.form.selectShift(option?.snapshot ?? null);
        }}
      >
        <option value="">{text.chooseShift}</option>
        {options.map((option) => (
          <option key={option.key} value={option.key}>
            {option.label}
          </option>
        ))}
      </AppSelect>
    </section>
  );
}
