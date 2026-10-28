#!/usr/bin/env python3
"""Measure real upload/worker inspection/download with full ClamAV signatures."""

import argparse
import hashlib
import json
import os
import platform
import signal
import threading
import time
import uuid

import database_recovery as files
from document_workload_fixture import DocumentWorkloadFixture
from document_workload_io import MIB, make_pdf, parallel_transfers, transfer
from measure_attendance import Api, WorkloadError, distribution
from payroll_workload_data import Actor
from workload_fixture import ROOT, workload_fixture


def prepare(fixture):
    api = Api(fixture.endpoint)
    try:
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            try:
                api.call("/auth/native/firebase")
                if fixture.sql("SELECT count(*) FROM accounts a JOIN platform_permissions p ON p.account_id=a.id WHERE a.email='workload-admin@example.invalid' AND p.permission='identity.manage'") == "1":
                    break
            except OSError:
                pass
            time.sleep(.5)
        else:
            raise WorkloadError("Document fixture API readiness timed out")
        actor = Actor(api, "workload-admin@example.invalid", fixture.bootstrap_password)
        company = actor.call("/companies", method="POST", body={"code": "DOCLOAD", "name": "Fictional Document Workload", "timezone": "UTC"})["id"]
        employee = str(uuid.uuid4())
        actor.call(f"/companies/{company}/employees", method="POST", body={"id": employee, "employeeNumber": "DOC-001",
            "person": {"id": str(uuid.uuid4()), "legalName": "Fictional Document Employee", "nationality": "ID"},
            "terms": {"effectiveFrom": "2026-01-01", "startDate": "2026-01-01", "status": "ACTIVE", "contract": "PERMANENT"},
            "reason": "Fictional document measurement"})
        fixture.start_worker()
        return api, actor, company, employee
    except BaseException:
        api.close()
        raise


def declare(actor, base, employee, source, label):
    document, revision = str(uuid.uuid4()), str(uuid.uuid4())
    actor.call(base + "/uploads", method="POST", body={"documentId": document, "revisionId": revision,
        "employmentId": employee, "title": label, "classification": "HR_ONLY", "expectedDocumentVersion": 0,
        "fileName": label + ".pdf", "mediaType": "application/pdf", "size": source["size"],
        "sha256": source["sha256"], "reason": "Fictional document workload"})
    return source | {"document": document, "revision": revision}


def validate(actor, base, records):
    started = time.monotonic()
    for record in records:
        path = base + "/revisions/" + record["revision"]
        revision = actor.call(path)
        if revision["uploadedBytes"] != record["size"]:
            raise WorkloadError("Uploaded document bytes did not match declaration")
        actor.call(path + "/validate", method="POST", body={"expectedVersion": revision["version"], "reason": "Measure actual content inspection"})
    deadline, progress, pending = started + 240, started + 30, list(records)
    while pending and time.monotonic() < deadline:
        for record in list(pending):
            revision = actor.call(base + "/revisions/" + record["revision"])
            if revision["status"] not in ("UPLOADING", "VALIDATING"):
                record.update({"status": revision["status"], "failureCode": revision["failureCode"],
                               "validationSeconds": round(time.monotonic() - started, 3)})
                pending.remove(record)
        if pending:
            if time.monotonic() >= progress:
                print(json.dumps({"phase": "validation", "pending": len(pending)}), flush=True)
                progress = time.monotonic() + 30
            time.sleep(.5)
    if pending:
        raise WorkloadError("Document validation phase exceeded four minutes")
    return round(time.monotonic() - started, 3)


def main(arguments=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--file-mib", type=int, default=100)
    parser.add_argument("--cpus", default="0,1,2,3")
    args = parser.parse_args(arguments)
    if not 1 <= args.file_mib <= 100:
        parser.error("Use 1–100 MiB per PDF")
    cpus = [int(part) for part in args.cpus.split(",")]
    os.umask(0o077)
    signal.signal(signal.SIGTERM, files.request_cancellation)
    api = None
    with workload_fixture(cpus, DocumentWorkloadFixture) as fixture:
        try:
            api, actor, company, employee = prepare(fixture)
            base = f"/companies/{company}/documents"
            records = []
            for index in range(2):
                path = fixture.directory / f"sample-{index}.pdf"
                source = make_pdf(path, args.file_mib * MIB, str(index))
                records.append(declare(actor, base, employee, source | {"path": path}, f"sample-{index}"))
            before = fixture.observations()
            print(json.dumps({"fixtureReady": True, "files": 2, "mibEach": args.file_mib,
                              "signatures": fixture.signature_info, "scannerVersion": fixture.scanner_version}), flush=True)
            token = actor.credentials["accessToken"]

            def upload(record, stop):
                samples, offset = [], 0
                with record["path"].open("rb") as source:
                    while body := source.read(MIB):
                        samples.append(transfer(fixture.endpoint, base + "/revisions/" + record["revision"] + "/chunks", token, stop,
                                                body=body, offset=offset))
                        offset += len(body)
                return samples

            started = time.monotonic()
            uploads = parallel_transfers(records, upload)
            upload_seconds = time.monotonic() - started
            validation_seconds = validate(actor, base, records)
            print(json.dumps({"phase": "inspected", "results": [{key: record[key] for key in ("status", "failureCode", "validationSeconds")} for record in records]}), flush=True)
            downloads, download_seconds = [], None
            if all(record["status"] == "READY" for record in records):
                started = time.monotonic()
                downloads = parallel_transfers(records * 2, lambda record, stop: transfer(fixture.endpoint,
                    base + "/revisions/" + record["revision"] + "/content", actor.credentials["accessToken"], stop,
                    expected_size=record["size"], expected_digest=record["sha256"]))
                download_seconds = time.monotonic() - started
            # The harmless standard antivirus test string proves that loaded
            # signatures reject content through this same worker/publication path.
            eicar = b"X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"
            test_record = declare(actor, base, employee, {"size": len(eicar), "sha256": hashlib.sha256(eicar).hexdigest()}, "antivirus-test")
            transfer(fixture.endpoint, base + "/revisions/" + test_record["revision"] + "/chunks", actor.credentials["accessToken"], threading.Event(), body=eicar)
            validate(actor, base, [test_record])
            after = fixture.observations()
            counts = json.loads(fixture.sql(f"""SELECT json_build_object(
                'revisions',count(*),'ready',count(*) FILTER(WHERE status='READY'),
                'unsafe',count(*) FILTER(WHERE status='REJECTED' AND failure_code='document_unsafe' AND scan_clean=false),
                'matchingReadyBytes',coalesce(sum(content_bytes) FILTER(WHERE status='READY' AND content_sha256=expected_sha256 AND content_bytes=expected_bytes AND detected_media_type='application/pdf' AND scan_clean=true),0),
                'scannerVersions',count(DISTINCT scanner_version),
                'published',(SELECT count(*) FROM documents WHERE company_id='{company}' AND current_revision_id IS NOT NULL),
                'audit',(SELECT count(*) FROM audit_entries WHERE company_id='{company}' AND action='documents.validation_completed'),
                'successfulJobs',(SELECT count(*) FROM document_validation_attempts a JOIN background_jobs j ON j.company_id=a.company_id AND j.id=a.job_id WHERE a.company_id='{company}' AND j.status='SUCCEEDED'),
                'chunks',(SELECT count(*) FROM document_upload_chunks WHERE company_id='{company}' AND status='COMMITTED'),
                'distinctChunkDigests',(SELECT count(DISTINCT sha256) FROM document_upload_chunks WHERE company_id='{company}' AND status='COMMITTED'))
                FROM document_revisions WHERE company_id='{company}';"""))
            resource_ok = all(len(observation) == 6 and
                all(s["running"] and not s["oomKilled"] and s["cpuSet"] == ",".join(map(str, cpus))
                    and "oom_kill 0" in s["cgroup"]["memory.events"] for s in observation.values())
                and sum(s["memoryLimit"] for s in observation.values()) == 16 * 1024 ** 3 for observation in (before, after))
            expected_chunks = 2 * args.file_mib + 1
            behavior_ok = resource_ok and len(downloads) == 4 and counts == {"revisions": 3, "ready": 2, "unsafe": 1,
                "matchingReadyBytes": 2 * args.file_mib * MIB, "scannerVersions": 1, "published": 2, "audit": 3,
                "successfulJobs": 3, "chunks": expected_chunks, "distinctChunkDigests": expected_chunks}
            report = {"format": 1, "scenario": "document-upload-inspection-download", "files": 2, "bytesPerFile": args.file_mib * MIB,
                "offeredConcurrentUploads": 2, "offeredConcurrentDownloads": 4, "pollIntervalSeconds": .5,
                "uploadSeconds": round(upload_seconds, 3), "validationSeconds": validation_seconds,
                "downloadSeconds": round(download_seconds, 3) if download_seconds is not None else None,
                "uploadChunkMilliseconds": distribution([sample["seconds"] * 1000 for batch in uploads for sample in batch]),
                "downloads": downloads, "results": [{key: record[key] for key in ("size", "sha256", "status", "failureCode", "validationSeconds")} for record in records],
                "antivirusTest": {key: test_record[key] for key in ("status", "failureCode", "validationSeconds")},
                "behaviorPassed": behavior_ok, "resourceContractPassed": resource_ok, "database": counts,
                "before": before, "after": after, "signatures": fixture.signature_info, "scannerVersion": fixture.scanner_version,
                "driverCpus": sorted(os.sched_getaffinity(0)), "kernel": platform.release(),
                "sourceCommit": files.captured(["git", "rev-parse", "HEAD"], fixture.log),
                "serviceConfigurationSha256": {name: files.sha256(ROOT / "deploy" / name)
                    for name in ("clamav/clamd.conf", "clamav/freshclam.conf", "garage/garage.toml")},
                "apiJarSha256": files.sha256(ROOT / "apps/server/build/libs/apps-server.jar"),
                "workerJarSha256": files.sha256(ROOT / "apps/worker/build/libs/apps-worker.jar")}
            fixture.retain_diagnostics = not behavior_ok
        except BaseException:
            fixture.retain_diagnostics = True
            raise
        finally:
            if api is not None:
                api.close()
    report["temporaryResourcesRemoved"] = True
    (ROOT / ".work").mkdir(exist_ok=True)
    destination = ROOT / ".work" / ("document-workload-" + fixture.run_id + ".json")
    files.write_json(destination, report)
    print(json.dumps({"report": str(destination), "behaviorPassed": behavior_ok, "database": counts}), flush=True)
    return 0 if behavior_ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
