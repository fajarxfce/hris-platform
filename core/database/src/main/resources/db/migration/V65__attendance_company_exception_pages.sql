CREATE INDEX attendance_company_days
    ON attendance_events(company_id, work_date DESC, employment_id, id);
