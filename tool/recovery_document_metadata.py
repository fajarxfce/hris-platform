"""Owned schema-103 recovery evidence; all identifiers and document facts are fictional."""

import hashlib
import json
import uuid

import database_recovery as database


def seed_metadata(api, endpoint, company, employee, token, container, log):
    document, revision = str(uuid.uuid4()), str(uuid.uuid4())
    path = "/companies/" + company + "/documents"
    metadata = {"category": "PASSPORT", "identityNumber": "RECOVERY-PASSPORT",
                "issuingCountry": "AU", "issuedOn": "2000-02-29", "validUntil": "2010-02-28"}
    api(endpoint, path + "/uploads", token=token, post=True, body={
        "documentId": document, "revisionId": revision, "employmentId": employee,
        "expectedDocumentVersion": 0, "title": "Fictional recovery passport",
        "classification": "PERSONAL", "fileName": "recovery.pdf", "mediaType": "application/pdf",
        "size": 3, "sha256": hashlib.sha256(b"pdf").hexdigest(),
        "reason": "Owned recovery evidence", "metadata": metadata})
    for version, number in ((1, "RECOVERY-CORRECTED"), (2, "RECOVERY-CURRENT")):
        metadata = metadata | {"identityNumber": number}
        api(endpoint, path + "/" + document + "/metadata", token=token, method="PUT", body={
            "expectedVersion": version, "metadata": metadata, "reason": "Owned recovery correction"})
    history = api(endpoint, path + "/" + document + "/metadata-history", token=token)
    assert len(history["items"]) == 3
    queries = {
        "head": "SELECT row_to_json(d)::text FROM documents d WHERE id='" + document + "'",
        "history": "SELECT json_agg(r ORDER BY version)::text FROM document_metadata_revisions r WHERE document_id='" + document + "'",
        "content": "SELECT row_to_json(r)::text FROM document_revisions r WHERE id='" + revision + "'",
    }
    captured = {name: json.loads(database.query(container, query, log)) for name, query in queries.items()}
    assert captured["head"]["metadata_version"] == 2 and captured["head"]["version"] == 3
    assert [row["identity_number"] for row in captured["history"]] == [
        "RECOVERY-PASSPORT", "RECOVERY-CORRECTED", "RECOVERY-CURRENT"]
    assert captured["content"]["status"] == "UPLOADING"
    return document, company, queries, captured


def verify_metadata(container, fixture, log):
    document, company, queries, captured = fixture
    for name, query in queries.items():
        assert json.loads(database.query(container, query, log)) == captured[name]
    assert database.query(container,
        "SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid='public.document_metadata_revisions'::regclass", log) == "t"
    for function in ("validate_document_metadata_revision", "initialize_document_metadata", "require_document_metadata_history"):
        assert database.query(container,
            "SELECT has_function_privilege('hris_app','" + function + "()','EXECUTE')", log) == "f"
    # Exercise the restored role and triggers, not just their catalog entries.
    for scope, count in ((company, 3), (str(uuid.uuid4()), 0)):
        database.query(container, "BEGIN; SET LOCAL ROLE hris_app; SELECT set_config('hris.company_id','" + scope + "',true); "
            "DO $$ BEGIN IF (SELECT count(*) FROM document_metadata_revisions WHERE document_id='" + document + "')<>" + str(count) + " "
            "THEN RAISE EXCEPTION 'Restored document history scope is incorrect'; END IF; END $$; ROLLBACK;", log)
    database.query(container, "DO $$ BEGIN "
        "BEGIN UPDATE document_metadata_revisions SET identity_number='changed' WHERE document_id='" + document + "'; "
        "RAISE EXCEPTION 'Restored document history accepted mutation'; EXCEPTION WHEN insufficient_privilege THEN NULL; END; "
        "BEGIN UPDATE documents SET version=version+1, metadata_version=metadata_version+1, identity_number='changed' WHERE id='" + document + "'; "
        "SET CONSTRAINTS ALL IMMEDIATE; RAISE EXCEPTION 'Restored document head accepted missing history'; "
        "EXCEPTION WHEN check_violation OR foreign_key_violation THEN NULL; END; END $$;", log)
    for name, query in queries.items():
        assert json.loads(database.query(container, query, log)) == captured[name]
