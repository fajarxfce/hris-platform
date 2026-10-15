-- Company calendars read one bounded month/day without scanning every employee's history.
CREATE INDEX leave_allocations_calendar
    ON leave_allocations(company_id, work_date, request_id)
    INCLUDE (employment_id, slot);
