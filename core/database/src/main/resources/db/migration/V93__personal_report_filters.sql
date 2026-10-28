-- Personal selections retain no report data and confer no access to their selected sources.
CREATE TABLE saved_report_filters (
    company_id uuid NOT NULL REFERENCES companies(id),
    account_id uuid NOT NULL REFERENCES accounts(id),
    id uuid NOT NULL,
    version bigint NOT NULL CHECK(version BETWEEN 0 AND 10000),
    created_at timestamptz NOT NULL CHECK(isfinite(created_at)),
    updated_at timestamptz NOT NULL CHECK(isfinite(updated_at) AND updated_at>=created_at),
    name varchar(80),
    kind varchar(32),
    definition_version varchar(64),
    as_of date CHECK(as_of IS NULL OR isfinite(as_of)),
    range_from varchar(10),
    range_until varchar(10),
    status varchar(32),
    selected_companies uuid[] NOT NULL DEFAULT '{}',
    PRIMARY KEY(company_id,account_id,id),
    CHECK((name IS NOT NULL AND version<=9999) OR (name IS NULL AND version>0)),
    CHECK(cardinality(selected_companies) BETWEEN 0 AND 32 AND array_position(selected_companies,NULL) IS NULL),
    CHECK(
        (name IS NULL AND kind IS NULL AND definition_version IS NULL AND as_of IS NULL
            AND range_from IS NULL AND range_until IS NULL AND status IS NULL AND cardinality(selected_companies)=0)
        OR (name IS NOT NULL AND length(name)>0 AND name=btrim(name)
            AND kind IN ('HEADCOUNT','ATTENDANCE_EXCEPTIONS','ATTENDANCE_CLOSINGS','LEAVE','OVERTIME','EXPENSES','PAYROLL_COSTS')
            AND kind IS NOT NULL AND definition_version IS NOT NULL
            AND ((kind='HEADCOUNT' AND as_of IS NOT NULL AND range_from IS NULL AND range_until IS NULL
                    AND status IS NULL AND cardinality(selected_companies) BETWEEN 1 AND 32)
                OR (kind<>'HEADCOUNT' AND as_of IS NULL AND range_from IS NOT NULL AND range_until IS NOT NULL
                    AND cardinality(selected_companies)=0 AND (status IS NULL OR kind='OVERTIME'))))
    )
);
CREATE INDEX saved_report_filter_active ON saved_report_filters(company_id,account_id,id) WHERE name IS NOT NULL;
ALTER TABLE saved_report_filters ENABLE ROW LEVEL SECURITY;
ALTER TABLE saved_report_filters FORCE ROW LEVEL SECURITY;
CREATE POLICY saved_report_filter_owner ON saved_report_filters
    USING(company_id=current_company_id() AND account_id=current_actor_id())
    WITH CHECK(company_id=current_company_id() AND account_id=current_actor_id());

CREATE FUNCTION protect_saved_report_filter() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Report filter deletion identity is retained' USING ERRCODE='42501';
    ELSIF TG_OP='INSERT' THEN
        IF NEW.version<>0 OR NEW.name IS NULL OR NEW.updated_at<>NEW.created_at THEN
            RAISE EXCEPTION 'Initial report filter state required' USING ERRCODE='23514';
        END IF;
    ELSIF OLD.name IS NULL OR NEW.version<>OLD.version+1 OR NEW.updated_at<OLD.updated_at
        OR (NEW.company_id,NEW.account_id,NEW.id,NEW.created_at)
            IS DISTINCT FROM (OLD.company_id,OLD.account_id,OLD.id,OLD.created_at) THEN
        RAISE EXCEPTION 'Report filter identity and versions are protected' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_saved_report_filter BEFORE INSERT OR UPDATE OR DELETE ON saved_report_filters
    FOR EACH ROW EXECUTE FUNCTION protect_saved_report_filter();
