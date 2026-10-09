CREATE FUNCTION current_read_company_ids() RETURNS uuid[] LANGUAGE plpgsql STABLE AS $$
DECLARE raw text:=current_setting('hris.read_company_ids',true); ids uuid[];
BEGIN
    IF raw IS NULL OR raw='' THEN RETURN ARRAY[]::uuid[]; END IF;
    IF length(raw)>1185 THEN RAISE EXCEPTION 'Read company scope is too large' USING ERRCODE='22023'; END IF;
    ids:=raw::uuid[];
    IF cardinality(ids)>32 OR coalesce(array_ndims(ids),1)<>1 THEN
        RAISE EXCEPTION 'Invalid read company scope' USING ERRCODE='22023';
    END IF;
    IF array_position(ids,NULL) IS NOT NULL THEN
        RAISE EXCEPTION 'Invalid read company scope' USING ERRCODE='22023';
    END IF;
    RETURN ids;
END $$;

-- Only report inputs gain this SELECT policy. Operational mutation policies remain unchanged.
DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['employments','employment_revisions','employment_revision_cancellations',
        'company_client_policy_heads','company_client_policy_revisions'] LOOP
        EXECUTE format('CREATE POLICY company_report_read ON %I FOR SELECT USING(current_company_id() IS NULL AND company_id=ANY(current_read_company_ids()))',t);
    END LOOP;
END $$;
