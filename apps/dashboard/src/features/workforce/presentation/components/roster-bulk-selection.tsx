import { Dismiss20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { useRosterBulkForm } from "../controllers/use-roster-bulk-form";
import { rosterBulkMessages } from "../i18n/roster-bulk-messages";

export function RosterBulkSelection(props: {
  form: ReturnType<typeof useRosterBulkForm>;
  locale: Locale;
}) {
  const { form, locale } = props;
  const text = rosterBulkMessages(locale);
  return (
    <section className="app-resource-panel app-roster-bulk-selection" aria-label={text.selected}>
      <div className="app-roster-bulk-selection-heading">
        <h2>
          {text.selected} ({form.employees.length})
        </h2>
        <AppButton
          appearance="subtle"
          disabled={form.employees.length === 0}
          onClick={form.clearEmployees}
        >
          {text.clear}
        </AppButton>
      </div>
      <p>{text.limit}</p>
      <p aria-live="polite">
        {text.dates}: {form.dates?.length ?? 0} · {text.entries}: {form.cellCount} / 200
      </p>
      {form.cellCount > 200 && <p role="alert">{text.overLimit}</p>}
      {form.invalidEmployees && (
        <p role="alert">{form.employeeLimit ? text.employeeLimit : text.noSelection}</p>
      )}
      {form.employees.length === 0 ? (
        <p>{text.noSelection}</p>
      ) : (
        <ul className="app-roster-bulk-selected-list">
          {form.employees.map((employee) => (
            <li key={employee.id}>
              <span>
                {employee.legalName} <span className="app-muted">{employee.employeeNumber}</span>
              </span>
              <AppButton
                appearance="subtle"
                icon={<Dismiss20Regular />}
                aria-label={`${text.remove} ${employee.legalName} (${employee.employeeNumber})`}
                onClick={() => form.removeEmployee(employee)}
              />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
