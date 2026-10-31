-- Extend the reviewed vocabulary; existing immutable policy revisions stay intact.
CREATE OR REPLACE FUNCTION valid_client_modules(names varchar[]) RETURNS boolean LANGUAGE sql IMMUTABLE STRICT AS $$
    SELECT cardinality(names)<=9
        AND (cardinality(names)=0 OR array_ndims(names)=1)
        AND array_position(names,NULL) IS NULL
        AND names<@ARRAY['PEOPLE','WORKFORCE','LEAVE','EXPENSES','PAYROLL','DOCUMENTS','COMMUNICATIONS','REPORTING','COLLABORATION']::varchar[]
        AND cardinality(names)=(SELECT count(DISTINCT item) FROM unnest(names) item)
$$;
