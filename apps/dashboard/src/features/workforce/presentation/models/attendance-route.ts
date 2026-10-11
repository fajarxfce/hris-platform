export const attendancePath = "/workforce/attendance";
export const employeeAttendancePath = (employee: string): string =>
  `${attendancePath}/${encodeURIComponent(employee)}`;
export const attendanceDayPath = (employee: string, date: string): string =>
  `${employeeAttendancePath(employee)}/days/${encodeURIComponent(date)}`;
