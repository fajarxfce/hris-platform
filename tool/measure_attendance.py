#!/usr/bin/env python3
"""Measure real attendance requests against an owned, resource-limited fixture."""

import argparse
import base64
from concurrent.futures import ThreadPoolExecutor, wait, FIRST_COMPLETED
from datetime import datetime, timedelta, timezone
import hashlib
import hmac
import http.client
import json
import math
import os
from pathlib import Path
import platform
import secrets
import signal
import struct
import threading
import time
from urllib.parse import urlsplit
import uuid

import database_recovery as files
from workload_fixture import ROOT, workload_fixture


class WorkloadError(RuntimeError):
    pass


class Api:
    def __init__(self, endpoint, stop=None):
        address = urlsplit(endpoint)
        self.connection = http.client.HTTPConnection(address.hostname, address.port, timeout=30)
        self.stop = stop or threading.Event()

    def close(self):
        self.connection.close()

    def call(self, path, *, body=None, token=None, method="GET", samples=None, label=None):
        if self.stop.is_set():
            raise WorkloadError("Workload was cancelled")
        headers = {"Content-Type": "application/json", "Idempotency-Key": str(uuid.uuid4())}
        if token:
            headers["Authorization"] = "Bearer " + token
        started = time.monotonic()
        deadline = started + 30
        status = 0
        response = None
        try:
            if self.connection.sock:
                self.connection.sock.settimeout(30)
            self.connection.request(method, "/api/v1" + path,
                                    json.dumps(body).encode() if body is not None else None, headers)
            response = self.connection.getresponse()
            status = response.status
            content = bytearray()
            while True:
                remaining = deadline - time.monotonic()
                if remaining <= 0 or self.stop.is_set():
                    raise WorkloadError("Workload request deadline or cancellation")
                if self.connection.sock:
                    self.connection.sock.settimeout(remaining)
                chunk = response.read1(64 * 1024)
                if not chunk:
                    break
                content.extend(chunk)
                if len(content) > 2 * 1024 ** 2:
                    raise WorkloadError("Workload response exceeded its bound")
            result = json.loads(content)
            if status != 200:
                code = result.get("code", "unclassified") if isinstance(result, dict) else "unclassified"
                # Only the stable symbolic code is diagnostic; bodies/identities stay private.
                safe_code = code if isinstance(code, str) and code.replace("_", "").isalnum() and len(code) <= 80 else "unclassified"
                raise WorkloadError(f"Workload HTTP {status}: {safe_code}")
            return result
        except BaseException:
            self.close()
            raise
        finally:
            if response is not None:
                response.close()
            if samples is not None:
                samples.append({"endpoint": label, "milliseconds": (time.monotonic() - started) * 1000, "status": status})


def code(secret):
    digest = hmac.new(base64.b32decode(secret), struct.pack(">Q", int(time.time()) // 30), hashlib.sha1).digest()
    offset = digest[-1] & 15
    return str((struct.unpack(">I", digest[offset:offset + 4])[0] & 0x7fffffff) % 1_000_000).zfill(6)


def prepare(fixture, count):
    api = Api(fixture.endpoint)
    try:
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            try:
                api.call("/auth/native/firebase")
                if fixture.sql("SELECT count(*) FROM accounts a JOIN platform_permissions p ON p.account_id=a.id WHERE a.email='workload-admin@example.invalid' AND p.permission='identity.manage'") == "1":
                    break
            except (OSError, http.client.HTTPException):
                pass
            time.sleep(0.5)
        else:
            raise WorkloadError("Workload API readiness timed out")
        login = api.call("/auth/native/login", method="POST", body={"email": "workload-admin@example.invalid", "password": fixture.bootstrap_password, "deviceName": "Fictional workload setup"})
        token = login["credentials"]["accessToken"]
        enrollment = api.call("/auth/native/mfa/enrollment", token=token, method="POST")
        token = api.call("/auth/native/mfa/enrollment/confirm", token=token, method="POST",
                         body={"operationId": enrollment["operationId"], "code": code(enrollment["secret"])})["credentials"]["accessToken"]
        company = api.call("/companies", token=token, method="POST", body={"code": "WORKLOAD", "name": "Fictional Workload Company", "timezone": "UTC"})["id"]
        started = datetime.now(timezone.utc).replace(minute=0, second=0, microsecond=0) - timedelta(hours=1)
        shift = str(uuid.uuid4())
        api.call(f"/companies/{company}/workforce/shifts/{shift}", token=token, method="PUT", body={
            "code": "REFERENCE", "name": "Fictional reference shift", "startsAt": started.strftime("%H:%M"),
            "endsAt": (started + timedelta(hours=8)).strftime("%H:%M"), "breakMinutes": 30,
            "timezone": "UTC", "mode": "REMOTE", "reason": "Fictional attendance workload",
        })
        actor = fixture.sql("SELECT id FROM accounts WHERE email='workload-admin@example.invalid'")
        participants = [{"account": str(uuid.uuid4()), "person": str(uuid.uuid4()), "employee": str(uuid.uuid4()),
                         "session": str(uuid.uuid4()), "device": str(uuid.uuid4()), "refresh": secrets.token_urlsafe(32)} for _ in range(count)]
        rows = "\n".join("\t".join([str(index + 1), p["account"], p["person"], p["employee"], p["session"],
                                   hashlib.sha256(p["refresh"].encode()).hexdigest()]) for index, p in enumerate(participants))
        days = {day: {"id": shift, "version": 0} for day in ("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")}
        # Only generated UUIDs, integers, a fixed JSON shape and a date enter SQL.
        fixture.sql(f"""BEGIN;
SET LOCAL hris.company_id='{company}'; SET LOCAL hris.actor_id='{actor}';
CREATE TEMP TABLE workload_people (n int, account uuid, person uuid, employee uuid, session uuid, refresh_hash text) ON COMMIT DROP;
COPY workload_people FROM STDIN;
{rows}
\\.
INSERT INTO accounts(id,email,display_name) SELECT account,'load-'||n||'@example.invalid','Fictional Employee '||n FROM workload_people;
INSERT INTO company_memberships(company_id,account_id) SELECT '{company}',account FROM workload_people;
INSERT INTO membership_permissions(company_id,account_id,permission) SELECT '{company}',account,'attendance.self.record' FROM workload_people;
INSERT INTO persons(id,owner_company_id,account_id,legal_name,nationality) SELECT person,'{company}',account,'Fictional Employee '||n,'ID' FROM workload_people;
INSERT INTO person_profile_revisions(person_id,revision,owner_company_id,legal_name,nationality,actor_id,reason,account_id)
SELECT person,0,'{company}','Fictional Employee '||n,'ID','{actor}','Fictional workload setup',account FROM workload_people;
INSERT INTO employments(company_id,id,person_id,employee_number) SELECT '{company}',employee,person,'LOAD-'||lpad(n::text,5,'0') FROM workload_people;
INSERT INTO employment_revisions(company_id,employment_id,revision,effective_from,contract_kind,start_date,status,actor_id,reason)
SELECT '{company}',employee,0,'2026-01-01','PERMANENT','2026-01-01','ACTIVE','{actor}','Fictional workload setup' FROM workload_people;
INSERT INTO native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,credential_version,created_at,rotated_at,device_name)
SELECT session,account,refresh_hash,now()-interval '1 minute',now()+interval '30 days',0,now()-interval '1 minute',now()-interval '1 minute','Fictional workload session' FROM workload_people;
COMMIT;
ANALYZE;
""")
        # The API creates the stored snapshot shape. Clone those reviewed facts for
        # the rest of the fictional cohort instead of inventing storage JSON.
        template_employee = participants[0]["employee"]
        api.call(f"/companies/{company}/workforce/employees/{template_employee}/schedule", token=token, method="PUT",
                 body={"effectiveFrom": "2026-01-01", "days": days, "reason": "Fictional workload template"})
        fixture.sql(f"""BEGIN;
SET LOCAL hris.company_id='{company}'; SET LOCAL hris.actor_id='{actor}';
INSERT INTO schedule_versions(company_id,employment_id)
SELECT company_id,id FROM employments WHERE company_id='{company}' AND id<>'{template_employee}';
INSERT INTO schedule_assignments(company_id,employment_id,revision,effective_from,days,actor_id,reason)
SELECT e.company_id,e.id,0,s.effective_from,s.days,'{actor}','Fictional workload template copy'
FROM employments e CROSS JOIN schedule_assignments s
WHERE e.company_id='{company}' AND e.id<>'{template_employee}'
AND s.company_id='{company}' AND s.employment_id='{template_employee}' AND s.revision=0;
COMMIT;
ANALYZE;
""")
        fixture.start_worker()
        # Warm refresh/window/read paths without adding an employee or captured event.
        participant = participants[0]
        refreshed = api.call("/auth/native/refresh", method="POST", body={"refreshToken": participant["refresh"]})
        participant["refresh"] = refreshed["refreshToken"]
        path = f"/companies/{company}/workforce/employees/{participant['employee']}/attendance"
        for index in range(30):
            if index < 3:
                api.call(path + "/windows", token=refreshed["accessToken"], method="POST", body={"deviceId": participant["device"]})
            api.call(path + f"?from={started.date()}&until={started.date()}", token=refreshed["accessToken"])
        # The real session policy retains its ten-second minimum rotation interval.
        time.sleep(11)
        return company, str(started.date()), participants
    finally:
        api.close()


def arrival(endpoint, company, work_date, participant, scheduled, stop):
    actual = time.monotonic()
    samples = []
    api = Api(endpoint, stop)
    try:
        credentials = api.call("/auth/native/refresh", method="POST", body={"refreshToken": participant["refresh"]}, samples=samples, label="refresh")
        token = credentials["accessToken"]
        path = f"/companies/{company}/workforce/employees/{participant['employee']}/attendance"
        window = api.call(path + "/windows", token=token, method="POST", body={"deviceId": participant["device"]}, samples=samples, label="captureWindow")
        event = str(uuid.uuid4())
        api.call(path, token=token, method="POST", body={"id": event, "workDate": work_date, "kind": "CLOCK_IN",
                 "capturedAt": datetime.now(timezone.utc).isoformat(), "windowId": window["id"], "deviceId": participant["device"], "offline": False}, samples=samples, label="clockIn")
        day = api.call(path + f"?from={work_date}&until={work_date}", token=token, samples=samples, label="attendanceRead")
        entries = [entry for summary in day for entry in summary["entries"]]
        if len(entries) != 1 or entries[0]["id"] != event or entries[0]["status"] != "ACCEPTED":
            raise WorkloadError("Attendance result did not contain exactly one accepted clock-in")
        return {"samples": samples, "arrivalLagMs": (actual - scheduled) * 1000,
                "scheduledCompletionMs": (time.monotonic() - scheduled) * 1000, "error": None}
    except Exception as error:
        return {"samples": samples, "arrivalLagMs": (actual - scheduled) * 1000,
                "scheduledCompletionMs": (time.monotonic() - scheduled) * 1000,
                "error": str(error) if isinstance(error, WorkloadError) else type(error).__name__}
    finally:
        api.close()


def run_arrivals(participants, duration, perform, *, concurrency=32):
    """Fixed arrivals, finite in-flight work and reported delay; no hidden retries."""
    started = time.monotonic()
    pending, completed = set(), []
    stop = threading.Event()
    executor = ThreadPoolExecutor(max_workers=concurrency, thread_name_prefix="attendance-load")
    next_index, next_progress = 0, started + 30
    try:
        while pending or next_index < len(participants):
            now = time.monotonic()
            if now > started + duration + 120:
                raise WorkloadError("Arrival workload exceeded its total deadline")
            scheduled = started + duration * next_index / len(participants)
            if next_index < len(participants) and now >= scheduled and len(pending) < concurrency:
                pending.add(executor.submit(perform, participants[next_index], scheduled, stop))
                next_index += 1
            elif pending:
                done, pending = wait(pending, timeout=min(0.1, max(0, scheduled - now)) if next_index < len(participants) and len(pending) < concurrency else 0.1, return_when=FIRST_COMPLETED)
                completed.extend(future.result() for future in done)
            else:
                stop.wait(min(0.1, max(0, scheduled - now)))
            if now >= next_progress:
                print(json.dumps({"scheduled": next_index, "completed": len(completed), "seconds": round(now - started, 1)}), flush=True)
                next_progress = now + 30
        return completed, time.monotonic() - started
    finally:
        stop.set()
        for future in pending:
            future.cancel()
        executor.shutdown(wait=True, cancel_futures=True)


def distribution(values):
    ordered = sorted(values)
    if not ordered:
        return {"count": 0}
    return {"count": len(ordered), "p50": round(ordered[math.ceil(len(ordered) * .50) - 1], 3),
            "p95": round(ordered[math.ceil(len(ordered) * .95) - 1], 3),
            "p99": round(ordered[math.ceil(len(ordered) * .99) - 1], 3), "max": round(ordered[-1], 3)}


def main(arguments=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--employees", type=int, default=5000)
    parser.add_argument("--arrival-seconds", type=int, default=900)
    parser.add_argument("--cpus", default="0,1,2,3")
    args = parser.parse_args(arguments)
    if not 1 <= args.employees <= 5000 or not 10 <= args.arrival_seconds <= 1800:
        parser.error("Use 1–5000 employees and 10–1800 arrival seconds")
    cpus = [int(part) for part in args.cpus.split(",")]
    os.umask(0o077)
    signal.signal(signal.SIGTERM, files.request_cancellation)
    with workload_fixture(cpus) as fixture:
        company, work_date, participants = prepare(fixture, args.employees)
        before = fixture.observations()
        print(json.dumps({"fixtureReady": True, "employees": args.employees, "arrivalSeconds": args.arrival_seconds, "sharedCpus": cpus}), flush=True)
        results, elapsed = run_arrivals(participants, args.arrival_seconds,
            lambda participant, scheduled, stop: arrival(fixture.endpoint, company, work_date, participant, scheduled, stop))
        after = fixture.observations()
        counts = fixture.sql(f"SELECT json_build_object('events',count(*),'accepted',count(*) FILTER(WHERE initial_status='ACCEPTED'),'employees',count(DISTINCT employment_id),'audit',(SELECT count(*) FROM audit_entries WHERE company_id='{company}' AND action='attendance.recorded'),'heads',(SELECT count(*) FROM attendance_sync_heads WHERE company_id='{company}')) FROM attendance_events WHERE company_id='{company}'")
        samples = [sample for result in results for sample in result["samples"]]
        metrics = {label: distribution([s["milliseconds"] for s in samples if s["endpoint"] == label]) for label in ("refresh", "captureWindow", "clockIn", "attendanceRead")}
        errors = {}
        for result in results:
            if result["error"]:
                errors[result["error"]] = errors.get(result["error"], 0) + 1
        report = {"format": 1, "scenario": "attendance-arrival", "employees": args.employees,
                  "arrivalSeconds": args.arrival_seconds, "elapsedSeconds": round(elapsed, 3),
                  "arrivalsPerSecond": args.employees / args.arrival_seconds, "maximumInFlight": 32,
                  "endpointMilliseconds": metrics, "arrivalLagMilliseconds": distribution([r["arrivalLagMs"] for r in results]),
                  "scheduledCompletionMilliseconds": distribution([r["scheduledCompletionMs"] for r in results]),
                  "errors": errors, "database": json.loads(counts), "before": before, "after": after,
                  "sourceCommit": files.captured(["git", "rev-parse", "HEAD"], fixture.log),
                  "kernel": platform.release(), "python": platform.python_version(),
                  "driverCpus": sorted(os.sched_getaffinity(0)),
                  "apiJarSha256": files.sha256(ROOT / "apps/server/build/libs/apps-server.jar"),
                  "workerJarSha256": files.sha256(ROOT / "apps/worker/build/libs/apps-worker.jar"),
                  "externalProvidersEnabled": False, "loginMeasured": False, "privateHttp": True}
        report["resourceContractPassed"] = all(
            all(service["running"] and not service["oomKilled"] and service["cpuSet"] == ",".join(map(str, cpus))
                and "oom_kill 0" in service["cgroup"]["memory.events"] for service in observation.values())
            and sum(service["memoryLimit"] for service in observation.values()) == 16 * 1024 ** 3
            for observation in (before, after))
        report["behaviorPassed"] = not errors and report["resourceContractPassed"] and report["database"] == {
            "events": args.employees, "accepted": args.employees, "employees": args.employees,
            "audit": args.employees, "heads": args.employees}
        if not report["behaviorPassed"]:
            fixture.retain_diagnostics = True
            for role, container in fixture.containers.items():
                with files.create_file(fixture.directory / (role + ".log")) as output:
                    files.command(["docker", "logs", "--tail", "150", container], output, stdout=output, allow_failure=True)
        report["latencyTargetPassed"] = report["behaviorPassed"] and all(v.get("p95", float("inf")) < 1000 for v in metrics.values()) and report["arrivalLagMilliseconds"].get("p95", float("inf")) < 100
    report["temporaryResourcesRemoved"] = True
    (ROOT / ".work").mkdir(exist_ok=True)
    destination = ROOT / ".work" / ("attendance-workload-" + fixture.run_id + ".json")
    files.write_json(destination, report)
    print(json.dumps({"report": str(destination), "behaviorPassed": report["behaviorPassed"], "latencyTargetPassed": report["latencyTargetPassed"], "errors": errors, "endpointMilliseconds": metrics}), flush=True)
    return 0 if report["behaviorPassed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
