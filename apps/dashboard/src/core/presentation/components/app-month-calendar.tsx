import { AppButton } from "./app-button";

export type MonthCalendarCell = Readonly<{
  id: string;
  date: string | null;
  number: string;
  label: string;
  count: string | null;
  selected: boolean;
  today: boolean;
}>;
export type MonthCalendarWeek = Readonly<{ id: string; days: readonly MonthCalendarCell[] }>;

export function AppMonthCalendar(props: {
  title: string;
  weekdays: readonly Readonly<{ id: string; label: string }>[];
  weeks: readonly MonthCalendarWeek[];
  onSelect: (date: string) => void;
}) {
  return (
    <table className="app-month-calendar" aria-label={props.title}>
      <thead>
        <tr>
          {props.weekdays.map((day) => (
            <th scope="col" key={day.id}>
              {day.label}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {props.weeks.map((week) => (
          <tr key={week.id}>
            {week.days.map((day) => (
              <td key={day.id}>
                {day.date === null ? (
                  <span aria-hidden="true" />
                ) : (
                  <AppButton
                    className="app-month-calendar-date"
                    appearance={day.selected ? "primary" : "transparent"}
                    aria-label={day.label}
                    aria-pressed={day.selected}
                    aria-current={day.today ? "date" : undefined}
                    data-today={day.today}
                    onClick={() => props.onSelect(day.date as string)}
                  >
                    <span className="app-month-calendar-number">{day.number}</span>
                    <span className="app-month-calendar-count" aria-hidden="true">
                      {day.count ?? "\u00a0"}
                    </span>
                  </AppButton>
                )}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
