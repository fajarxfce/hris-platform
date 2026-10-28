#!/usr/bin/env python3
"""Measure one actual worker payroll calculation on an owned four-CPU deployment."""

import argparse
from decimal import Decimal
import json
import os
import platform
import signal
import time
import uuid

import database_recovery as files
from measure_attendance import WorkloadError
from payroll_workload_data import prepare_payroll
from workload_fixture import ROOT, workload_fixture


def payroll_outcome_passes(counts, view, employees):
    # Fast completion is meaningful only for the full successful cohort. Keep
    # monetary reconciliation decimal even beyond binary float's exact range.
    amounts_match = counts["amountVariants"] == 1 and all(counts[total] is not None and counts[one] is not None
        and Decimal(counts[total]) == employees * Decimal(counts[one])
        for total, one in (("taxableGross", "oneTaxableGross"), ("withheld", "oneWithheld"), ("takeHome", "oneTakeHome")))
    return amounts_match and all(counts[name] == employees for name in ("results", "employees", "succeeded", "audit")) \
        and view["run"]["status"] == "CALCULATED" and view["run"]["failed"] == 0 and view["job"]["status"] == "SUCCEEDED"


def main(arguments=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--employees", type=int, default=5000)
    parser.add_argument("--cpus", default="0,1,2,3")
    args = parser.parse_args(arguments)
    if not 2 <= args.employees <= 5000:
        parser.error("Use 2–5000 employees to exercise both canonical and cloned sources")
    cpus = [int(part) for part in args.cpus.split(",")]
    os.umask(0o077)
    signal.signal(signal.SIGTERM, files.request_cancellation)
    api = None
    with workload_fixture(cpus) as fixture:
        try:
            preparation_started = time.monotonic()
            api, preparer, reviewer, company, period, month, work_version = prepare_payroll(fixture, args.employees)
            prepared_seconds = time.monotonic() - preparation_started
            before = fixture.observations()
            base = f"/companies/{company}/payroll"
            run = str(uuid.uuid4())
            print(json.dumps({"fixtureReady": True, "employees": args.employees, "preparationSeconds": round(prepared_seconds, 3)}), flush=True)
            started = time.monotonic()
            preparer.call(base + f"/periods/{period}/runs", method="POST", body={"id": run, "incomeDueDate": month + "-31",
                "expectedPeriodVersion": 0, "expectedWorkPeriodVersion": work_version, "expectedPolicyVersion": 0,
                "reviewReference": "Fictional complete cohort calculation", "reason": "Measure the real payroll worker"})
            admission_seconds = time.monotonic() - started
            deadline, progress = started + 1200, started + 30
            while time.monotonic() < deadline:
                view = reviewer.call(base + f"/runs/{run}?limit=1")
                if view["run"]["status"] != "PROCESSING":
                    break
                if time.monotonic() >= progress:
                    print(json.dumps({"processed": view["run"]["processed"], "succeeded": view["run"]["succeeded"],
                                      "failed": view["run"]["failed"], "seconds": round(time.monotonic() - started, 1)}), flush=True)
                    progress = time.monotonic() + 30
                time.sleep(1)
            else:
                raise WorkloadError("Payroll measurement exceeded twenty minutes")
            elapsed = time.monotonic() - started
            after = fixture.observations()
            counts = json.loads(fixture.sql(f"""SELECT json_build_object(
                'results',count(*),'employees',count(DISTINCT t.employment_id),
                'succeeded',count(*) FILTER(WHERE r.status='SUCCEEDED'),
                'amountVariants',count(DISTINCT (r.taxable_gross,r.withheld,r.take_home)),
                'taxableGross',sum(r.taxable_gross)::text,'withheld',sum(r.withheld)::text,'takeHome',sum(r.take_home)::text,
                'oneTaxableGross',min(r.taxable_gross)::text,'oneWithheld',min(r.withheld)::text,'oneTakeHome',min(r.take_home)::text,
                'audit',(SELECT count(*) FROM audit_entries WHERE company_id='{company}' AND action='payroll.employee_calculated'),
                'maxFactBytes',max(octet_length(r.facts::text)), 'maxCalculationBytes',max(octet_length(r.calculation::text)))
                FROM payroll_run_results r JOIN payroll_run_targets t ON t.company_id=r.company_id AND t.run_id=r.run_id AND t.ordinal=r.ordinal
                WHERE r.company_id='{company}' AND r.run_id='{run}';"""))
            failures = json.loads(fixture.sql(f"SELECT coalesce(json_object_agg(code,n),'{{}}'::json) FROM(SELECT failure_code code,count(*) n FROM payroll_run_results WHERE company_id='{company}' AND run_id='{run}' AND status='FAILED' GROUP BY failure_code) f;"))
            resource_ok = all(
                all(s["running"] and not s["oomKilled"] and s["cpuSet"] == ",".join(map(str, cpus))
                    and "oom_kill 0" in s["cgroup"]["memory.events"] for s in observation.values())
                and sum(s["memoryLimit"] for s in observation.values()) == 16 * 1024 ** 3
                for observation in (before, after))
            behavior_ok = resource_ok and payroll_outcome_passes(counts, view, args.employees)
            report = {"format": 1, "scenario": "payroll-calculation", "employees": args.employees, "earningsMonth": month,
                "preparationSeconds": round(prepared_seconds, 3), "admissionSeconds": round(admission_seconds, 3),
                "elapsedSeconds": round(elapsed, 3), "pollIntervalSeconds": 1,
                "behaviorPassed": behavior_ok, "resourceContractPassed": resource_ok, "tenMinuteTargetPassed": behavior_ok and elapsed < 600,
                "database": counts, "failures": failures, "status": view["run"]["status"], "jobStatus": view["job"]["status"],
                "before": before, "after": after, "driverCpus": sorted(os.sched_getaffinity(0)),
                "sourceCommit": files.captured(["git", "rev-parse", "HEAD"], fixture.log), "kernel": platform.release(),
                "apiJarSha256": files.sha256(ROOT / "apps/server/build/libs/apps-server.jar"),
                "workerJarSha256": files.sha256(ROOT / "apps/worker/build/libs/apps-worker.jar"),
                "reviewAndPublicationMeasured": False, "externalProvidersEnabled": False}
            if not behavior_ok:
                fixture.retain_diagnostics = True
        except BaseException:
            fixture.retain_diagnostics = True
            raise
        finally:
            if api is not None:
                api.close()
            if fixture.retain_diagnostics:
                for role, container in fixture.containers.items():
                    with files.create_file(fixture.directory / (role + ".log")) as output:
                        files.command(["docker", "logs", "--tail", "150", container], output, stdout=output, allow_failure=True)
    report["temporaryResourcesRemoved"] = True
    (ROOT / ".work").mkdir(exist_ok=True)
    destination = ROOT / ".work" / ("payroll-workload-" + fixture.run_id + ".json")
    files.write_json(destination, report)
    print(json.dumps({"report": str(destination), "behaviorPassed": behavior_ok, "tenMinuteTargetPassed": report["tenMinuteTargetPassed"],
                      "elapsedSeconds": report["elapsedSeconds"], "failures": failures}), flush=True)
    return 0 if behavior_ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
